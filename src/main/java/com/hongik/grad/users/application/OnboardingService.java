package com.hongik.grad.users.application;

import com.hongik.grad.auth.principal.AuthUser;
import com.hongik.grad.folders.domain.Folder;
import com.hongik.grad.folders.domain.FolderRepository;
import com.hongik.grad.folders.domain.FolderType;
import com.hongik.grad.users.domain.Profile;
import com.hongik.grad.users.domain.ProfileRepository;
import com.hongik.grad.users.dto.OnboardingRequest;
import com.hongik.grad.users.dto.ProfileResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent onboarding: ensure exactly one {@link Profile} and one {@link FolderType#DEFAULT}
 * folder exist for the authenticated user.
 *
 * <p>Re-calling with a different nickname updates the profile name but never creates a second
 * default folder.
 */
@Service
public class OnboardingService {

  private final ProfileRepository profileRepository;
  private final FolderRepository folderRepository;

  public OnboardingService(ProfileRepository profileRepository, FolderRepository folderRepository) {
    this.profileRepository = profileRepository;
    this.folderRepository = folderRepository;
  }

  @Transactional
  public ProfileResponse onboard(AuthUser user, OnboardingRequest request) {
    Profile profile =
        profileRepository
            .findById(user.id())
            .map(
                existing -> {
                  existing.updateNickname(request.nickname());
                  return existing;
                })
            .orElseGet(() -> profileRepository.save(new Profile(user.id(), request.nickname())));

    Folder folder =
        folderRepository
            .findByUserIdAndType(user.id(), FolderType.DEFAULT)
            .orElseGet(() -> folderRepository.save(Folder.defaultFor(user.id())));

    return new ProfileResponse(
        profile.getId(), profile.getNickname(), folder.getId(), profile.getCreatedAt());
  }
}
