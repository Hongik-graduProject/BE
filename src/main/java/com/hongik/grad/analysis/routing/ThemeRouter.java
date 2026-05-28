package com.hongik.grad.analysis.routing;

import com.hongik.grad.disclosures.domain.Disclosure;
import java.util.Optional;

/**
 * Selects which theme a disclosure-level report should be routed to. v1.0 ships {@link
 * PrimaryFixedRouter}; v5 will swap in a cosine-similarity router without touching callers.
 */
public interface ThemeRouter {

  Optional<RoutingResult> route(Disclosure disclosure);
}
