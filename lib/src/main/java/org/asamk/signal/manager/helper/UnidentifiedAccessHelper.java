package org.asamk.signal.manager.helper;

import org.asamk.signal.manager.SignalDependencies;
import org.asamk.signal.manager.api.PhoneNumberSharingMode;
import org.asamk.signal.manager.storage.SignalAccount;
import org.asamk.signal.manager.storage.recipients.RecipientId;
import org.signal.libsignal.metadata.certificate.InvalidCertificateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccessPair;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static org.whispersystems.signalservice.internal.util.Util.getSecretBytes;

public class UnidentifiedAccessHelper {

    private final static Logger logger = LoggerFactory.getLogger(UnidentifiedAccessHelper.class);

    private final SignalAccount account;
    private final SignalDependencies dependencies;
    private final SelfProfileKeyProvider selfProfileKeyProvider;
    private final ProfileProvider profileProvider;

    public UnidentifiedAccessHelper(
            final SignalAccount account,
            final SignalDependencies dependencies,
            final SelfProfileKeyProvider selfProfileKeyProvider,
            final ProfileProvider profileProvider
    ) {
        this.account = account;
        this.dependencies = dependencies;
        this.selfProfileKeyProvider = selfProfileKeyProvider;
        this.profileProvider = profileProvider;
    }

    private byte[] getSenderCertificateFor(final RecipientId recipientId) {
        final var sharingMode = account.getConfigurationStore().getPhoneNumberSharingMode();
        if (sharingMode == PhoneNumberSharingMode.EVERYBODY || (
                sharingMode == PhoneNumberSharingMode.CONTACTS
                        && account.getContactStore().getContact(recipientId) != null
        )) {
            logger.debug("Using normal sender certificate for message to {}", recipientId);
            return getSenderCertificate();
        } else {
            logger.debug("Using phone number privacy sender certificate for message to {}", recipientId);
            return getSenderCertificateForPhoneNumberPrivacy();
        }
    }

    private byte[] getSenderCertificateForPhoneNumberPrivacy() {
        // TODO cache for a day
        try {
            return dependencies.getAccountManager().getSenderCertificateForPhoneNumberPrivacy();
        } catch (IOException e) {
            logger.warn("Failed to get sender certificate, ignoring: {}", e.getMessage());
            return null;
        }
    }

    private byte[] getSenderCertificate() {
        // TODO cache for a day
        try {
            return dependencies.getAccountManager().getSenderCertificate();
        } catch (IOException e) {
            logger.warn("Failed to get sender certificate, ignoring: {}", e.getMessage());
            return null;
        }
    }

    private byte[] getSelfUnidentifiedAccessKey() {
        return UnidentifiedAccess.deriveAccessKeyFrom(selfProfileKeyProvider.getProfileKey());
    }

    public byte[] getTargetUnidentifiedAccessKey(RecipientId recipient) {
        var targetProfile = profileProvider.getProfile(recipient);
        if (targetProfile == null) {
            return null;
        }

        switch (targetProfile.getUnidentifiedAccessMode()) {
            case ENABLED:
                var theirProfileKey = account.getProfileStore().getProfileKey(recipient);
                if (theirProfileKey == null) {
                    return null;
                }

                return UnidentifiedAccess.deriveAccessKeyFrom(theirProfileKey);
            case UNRESTRICTED:
                return createUnrestrictedUnidentifiedAccess();
            default:
                return null;
        }
    }

    public Optional<UnidentifiedAccessPair> getAccessForSync() {
        var selfUnidentifiedAccessKey = getSelfUnidentifiedAccessKey();
        var selfUnidentifiedAccessCertificate = getSenderCertificate();

        if (selfUnidentifiedAccessKey == null || selfUnidentifiedAccessCertificate == null) {
            return Optional.absent();
        }

        try {
            return Optional.of(new UnidentifiedAccessPair(new UnidentifiedAccess(selfUnidentifiedAccessKey,
                    selfUnidentifiedAccessCertificate),
                    new UnidentifiedAccess(selfUnidentifiedAccessKey, selfUnidentifiedAccessCertificate)));
        } catch (InvalidCertificateException e) {
            return Optional.absent();
        }
    }

    public List<Optional<UnidentifiedAccessPair>> getAccessFor(List<RecipientId> recipients) {
        return recipients.stream().map(this::getAccessFor).collect(Collectors.toList());
    }

    public Optional<UnidentifiedAccessPair> getAccessFor(RecipientId recipient) {
        var recipientUnidentifiedAccessKey = getTargetUnidentifiedAccessKey(recipient);
        var selfUnidentifiedAccessKey = getSelfUnidentifiedAccessKey();
        var selfUnidentifiedAccessCertificate = getSenderCertificateFor(recipient);

        if (recipientUnidentifiedAccessKey == null
                || selfUnidentifiedAccessKey == null
                || selfUnidentifiedAccessCertificate == null) {
            return Optional.absent();
        }

        try {
            return Optional.of(new UnidentifiedAccessPair(new UnidentifiedAccess(recipientUnidentifiedAccessKey,
                    selfUnidentifiedAccessCertificate),
                    new UnidentifiedAccess(selfUnidentifiedAccessKey, selfUnidentifiedAccessCertificate)));
        } catch (InvalidCertificateException e) {
            return Optional.absent();
        }
    }

    private static byte[] createUnrestrictedUnidentifiedAccess() {
        return getSecretBytes(16);
    }
}
