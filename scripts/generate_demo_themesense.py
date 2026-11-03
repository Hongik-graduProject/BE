"""데모용 ThemeSense 산출물 3 개를 만든다 (themes_catalog / stage1_predictions / stage2_coefficients).

실제 ThemeSense 모델 출력이 아니다. DART 회사개황의 업종코드(KSIC)를 규칙으로 테마에 매핑하고,
테마별 재무지표 β 는 손으로 정한 값이다. KOSPI(Y)·KOSDAQ(K) 상장 전 종목을 대상으로 한다.

사용:
    DART_API_KEY=... python3 scripts/generate_demo_themesense.py \
        --out demo/themesense --cache /tmp/dart_company_cache.json

DART 호출: corpCode.xml 1 회 + 종목당 company.json 1 회 (약 4,000 회, 초당 약 8 회로 제한).
캐시 파일이 있으면 이미 받은 회사는 다시 부르지 않는다.
"""

import argparse
import hashlib
import io
import json
import os
import sys
import threading
import time
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
import zipfile
from concurrent.futures import ThreadPoolExecutor

DART = "https://opendart.fss.or.kr/api"
SOURCE = "ThemeSense demo (KSIC rule-based, not a model output)"

# (테마, 설명, 관련 테마 2 개) — 관련 테마는 stage1 의 2·3 순위가 된다.
THEMES = [
    ("AI/반도체", "반도체 제조·장비·소재", ["IT하드웨어/전자부품", "소프트웨어/인터넷"]),
    ("IT하드웨어/전자부품", "전자부품·컴퓨터·통신장비·디스플레이", ["AI/반도체", "자동차/모빌리티"]),
    ("2차전지/EV", "전지·전기차 부품", ["화학/소재", "자동차/모빌리티"]),
    ("바이오/헬스케어", "의약품·의료기기·연구개발", ["화학/소재", "소비재/유통"]),
    ("자동차/모빌리티", "완성차·자동차 부품", ["2차전지/EV", "철강/금속"]),
    ("조선/방산/우주", "선박·항공·방위산업", ["기계/산업재", "철강/금속"]),
    ("화학/소재", "화학·고무·플라스틱·비금속 소재", ["2차전지/EV", "에너지/유틸리티"]),
    ("철강/금속", "1차 금속·금속가공", ["기계/산업재", "건설/부동산"]),
    ("기계/산업재", "기계·장비·전기장비", ["철강/금속", "조선/방산/우주"]),
    ("소프트웨어/인터넷", "소프트웨어·인터넷·통신서비스", ["AI/반도체", "미디어/엔터/게임"]),
    ("미디어/엔터/게임", "방송·영상·음악·게임", ["소프트웨어/인터넷", "소비재/유통"]),
    ("금융/지주", "은행·증권·보험·지주회사", ["건설/부동산", "소비재/유통"]),
    ("건설/부동산", "건설·부동산", ["철강/금속", "금융/지주"]),
    ("소비재/유통", "식품·의류·생활용품·유통", ["미디어/엔터/게임", "운송/물류"]),
    ("에너지/유틸리티", "석유·전기·가스·환경", ["화학/소재", "건설/부동산"]),
    ("운송/물류", "육상·해상·항공 운송", ["소비재/유통", "에너지/유틸리티"]),
    ("기타 서비스", "전문·사업·교육 서비스", ["소프트웨어/인터넷", "소비재/유통"]),
]

# KSIC 코드 접두사 → 테마. 가장 긴 접두사가 이긴다.
KSIC_RULES = {
    "261": "AI/반도체",
    "26": "IT하드웨어/전자부품",
    "282": "2차전지/EV",
    "28": "기계/산업재",
    "29": "기계/산업재",
    "21": "바이오/헬스케어",
    "271": "바이오/헬스케어",
    "27": "IT하드웨어/전자부품",
    "701": "바이오/헬스케어",
    "86": "바이오/헬스케어",
    "30": "자동차/모빌리티",
    "311": "조선/방산/우주",
    "313": "조선/방산/우주",
    "252": "조선/방산/우주",
    "31": "자동차/모빌리티",
    "20": "화학/소재",
    "22": "화학/소재",
    "23": "화학/소재",
    "24": "철강/금속",
    "25": "철강/금속",
    "5821": "미디어/엔터/게임",
    "58": "소프트웨어/인터넷",
    "61": "소프트웨어/인터넷",
    "62": "소프트웨어/인터넷",
    "63": "소프트웨어/인터넷",
    "59": "미디어/엔터/게임",
    "60": "미디어/엔터/게임",
    "90": "미디어/엔터/게임",
    "91": "미디어/엔터/게임",
    "64": "금융/지주",
    "65": "금융/지주",
    "66": "금융/지주",
    "41": "건설/부동산",
    "42": "건설/부동산",
    "68": "건설/부동산",
    "05": "에너지/유틸리티",
    "06": "에너지/유틸리티",
    "19": "에너지/유틸리티",
    "35": "에너지/유틸리티",
    "36": "에너지/유틸리티",
    "37": "에너지/유틸리티",
    "38": "에너지/유틸리티",
    "39": "에너지/유틸리티",
    "49": "운송/물류",
    "50": "운송/물류",
    "51": "운송/물류",
    "52": "운송/물류",
}
for _p in ["10", "11", "12", "13", "14", "15", "16", "17", "18", "32", "33", "45", "46", "47",
           "55", "56", "96"]:
    KSIC_RULES[_p] = "소비재/유통"

# 업종코드만으로는 대표 테마가 어긋나는 대형주 보정 (예: 삼성전자 KSIC 264 = 통신장비).
OVERRIDES = {
    "005930": "AI/반도체",
    "000660": "AI/반도체",
    "373220": "2차전지/EV",
    "006400": "2차전지/EV",
    "051910": "2차전지/EV",
    "035420": "소프트웨어/인터넷",
    "035720": "소프트웨어/인터넷",
    "012450": "조선/방산/우주",
    "005380": "자동차/모빌리티",
    "000270": "자동차/모빌리티",
}

# 테마별 β (metric_code, beta). 지표 코드는 BE MetricCanonicalCode alias 와 같다.
# Δ 는 연간 대 연간: op_margin·net_margin·debt_ratio 는 %p 차이, rev_gr 는 YoY %.
BETAS = {
    "AI/반도체": [("rev_gr", 0.08), ("op_margin", 0.35), ("net_margin", 0.15), ("per", -0.02), ("debt_ratio", -0.01)],
    "IT하드웨어/전자부품": [("op_margin", 0.30), ("rev_gr", 0.06), ("net_margin", 0.12), ("debt_ratio", -0.02), ("per", -0.02)],
    "2차전지/EV": [("rev_gr", 0.10), ("op_margin", 0.25), ("debt_ratio", -0.03), ("net_margin", 0.10), ("per", -0.01)],
    "바이오/헬스케어": [("rev_gr", 0.07), ("net_margin", 0.10), ("op_margin", 0.12), ("debt_ratio", -0.02), ("pbr", -0.05)],
    "자동차/모빌리티": [("op_margin", 0.32), ("rev_gr", 0.05), ("net_margin", 0.15), ("debt_ratio", -0.02), ("per", -0.03)],
    "조선/방산/우주": [("rev_gr", 0.09), ("op_margin", 0.28), ("debt_ratio", -0.03), ("net_margin", 0.10), ("per", -0.01)],
    "화학/소재": [("op_margin", 0.30), ("rev_gr", 0.05), ("debt_ratio", -0.03), ("net_margin", 0.12), ("pbr", -0.08)],
    "철강/금속": [("op_margin", 0.28), ("rev_gr", 0.04), ("debt_ratio", -0.03), ("net_margin", 0.12), ("pbr", -0.10)],
    "기계/산업재": [("op_margin", 0.26), ("rev_gr", 0.06), ("net_margin", 0.12), ("debt_ratio", -0.02), ("per", -0.02)],
    "소프트웨어/인터넷": [("rev_gr", 0.09), ("op_margin", 0.22), ("net_margin", 0.12), ("per", -0.02), ("debt_ratio", -0.01)],
    "미디어/엔터/게임": [("rev_gr", 0.08), ("op_margin", 0.24), ("net_margin", 0.10), ("per", -0.02), ("debt_ratio", -0.01)],
    "금융/지주": [("net_margin", 0.20), ("op_margin", 0.15), ("rev_gr", 0.03), ("pbr", -0.15), ("debt_ratio", -0.01)],
    "건설/부동산": [("op_margin", 0.25), ("debt_ratio", -0.04), ("rev_gr", 0.04), ("net_margin", 0.12), ("pbr", -0.10)],
    "소비재/유통": [("op_margin", 0.27), ("rev_gr", 0.05), ("net_margin", 0.14), ("debt_ratio", -0.02), ("per", -0.02)],
    "에너지/유틸리티": [("op_margin", 0.24), ("debt_ratio", -0.03), ("rev_gr", 0.04), ("net_margin", 0.12), ("pbr", -0.08)],
    "운송/물류": [("op_margin", 0.26), ("rev_gr", 0.05), ("debt_ratio", -0.03), ("net_margin", 0.12), ("per", -0.02)],
    "기타 서비스": [("op_margin", 0.22), ("rev_gr", 0.05), ("net_margin", 0.12), ("debt_ratio", -0.02), ("per", -0.02)],
}


def get(url, params, timeout=30):
    q = urllib.parse.urlencode(params)
    with urllib.request.urlopen(f"{url}?{q}", timeout=timeout) as r:
        return r.read()


def listed_corps(key):
    """corpCode.xml 에서 stock_code 가 있는 (상장) 회사만."""
    raw = get(f"{DART}/corpCode.xml", {"crtfc_key": key}, timeout=120)
    with zipfile.ZipFile(io.BytesIO(raw)) as z:
        root = ET.fromstring(z.read(z.namelist()[0]))
    out = []
    for e in root.iter("list"):
        stock = (e.findtext("stock_code") or "").strip()
        if stock:
            out.append((stock, e.findtext("corp_code").strip(), e.findtext("corp_name").strip()))
    return out


def theme_for(ticker, induty):
    if ticker in OVERRIDES:
        return OVERRIDES[ticker]
    for n in range(len(induty), 0, -1):
        t = KSIC_RULES.get(induty[:n])
        if t:
            return t
    return "기타 서비스"


def jitter(ticker, salt):
    """종목마다 다르지만 실행마다 같은 0~1 값."""
    return int(hashlib.sha256(f"{ticker}:{salt}".encode()).hexdigest()[:8], 16) / 0xFFFFFFFF


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="demo/themesense")
    ap.add_argument("--cache", default="dart_company_cache.json")
    ap.add_argument("--rate", type=float, default=8.0, help="DART 초당 호출 상한")
    args = ap.parse_args()
    key = os.environ.get("DART_API_KEY")
    if not key:
        sys.exit("DART_API_KEY 가 필요하다")

    cache = json.load(open(args.cache)) if os.path.exists(args.cache) else {}
    corps = listed_corps(key)
    print(f"listed corps: {len(corps)}, cached: {len(cache)}", flush=True)

    # 호출 한 번이 1 초 가까이 걸려 순차로는 너무 느리다. 시작 간격만 1/rate 로 제한하고 병렬로 부른다.
    lock = threading.Lock()
    next_slot = [time.monotonic()]

    def fetch(corp_code):
        with lock:
            wait = next_slot[0] - time.monotonic()
            next_slot[0] = max(next_slot[0], time.monotonic()) + 1.0 / args.rate
        if wait > 0:
            time.sleep(wait)
        return corp_code, json.loads(
            get(f"{DART}/company.json", {"crtfc_key": key, "corp_code": corp_code}))

    pending = [c for _, c, _ in corps if c not in cache]
    with ThreadPoolExecutor(max_workers=8) as pool:
        for i, (corp_code, body) in enumerate(pool.map(fetch, pending), start=1):
            if body.get("status") == "020":
                json.dump(cache, open(args.cache, "w"))
                sys.exit("DART 호출 한도 초과 — 캐시를 남기고 중단한다. 내일 다시 실행하면 이어서 진행된다.")
            if body.get("status") == "000":
                cache[corp_code] = {"corp_cls": body.get("corp_cls", ""),
                                    "induty_code": body.get("induty_code", "")}
            if i % 200 == 0:
                json.dump(cache, open(args.cache, "w"))
                print(f"  {i}/{len(pending)}", flush=True)
    json.dump(cache, open(args.cache, "w"))

    names = [t[0] for t in THEMES]
    related = {t[0]: t[2] for t in THEMES}
    stage1 = []
    counts = {}
    for ticker, corp_code, _ in sorted(corps):
        info = cache.get(corp_code)
        if not info or info["corp_cls"] not in ("Y", "K"):
            continue
        primary = theme_for(ticker, info["induty_code"])
        p1 = round(0.55 + 0.30 * jitter(ticker, 1), 2)
        p2 = round((1 - p1) * (0.55 + 0.25 * jitter(ticker, 2)), 2)
        p3 = round(1 - p1 - p2, 2)
        ranked = [primary] + related[primary]
        stage1.append({
            "ticker": ticker,
            "predictions": [
                {"class_idx": names.index(n), "theme_name": n, "probability": p, "rank": r}
                for r, (n, p) in enumerate(zip(ranked, [p1, p2, p3]), start=1)
            ],
        })
        counts[primary] = counts.get(primary, 0) + 1

    catalog = [
        {"class_idx": i, "name": n, "description": d, "source": SOURCE}
        for i, (n, d, _) in enumerate(THEMES)
    ]
    stage2 = []
    for n in names:
        coefs = sorted(BETAS[n], key=lambda c: -abs(c[1]))
        stage2.append({
            "theme_name": n,
            "model_version": "demo",
            "coefficients": [
                {"metric_code": m, "beta": b, "abs_beta": abs(b), "rank": r,
                 "direction": "positive" if b > 0 else "negative",
                 "r_squared": None, "sample_size": counts.get(n, 0)}
                for r, (m, b) in enumerate(coefs, start=1)
            ],
        })

    os.makedirs(args.out, exist_ok=True)
    for fname, data in [("themes_catalog.json", catalog), ("stage1_predictions.json", stage1),
                        ("stage2_coefficients.json", stage2)]:
        with open(os.path.join(args.out, fname), "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=1)
    print(f"stage1 tickers: {len(stage1)}")
    for n in names:
        print(f"  {n}: {counts.get(n, 0)}")


if __name__ == "__main__":
    main()
