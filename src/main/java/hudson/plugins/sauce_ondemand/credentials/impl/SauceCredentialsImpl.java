package hudson.plugins.sauce_ondemand.credentials.impl;

import com.cloudbees.plugins.credentials.*;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.domains.HostnamePortRequirement;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.google.common.base.Strings;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.util.Secret;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Created by gavinmogan on 10/17/15.
 */
public class SauceCredentialsImpl extends BaseStandardCredentials implements StandardUsernamePasswordCredentials {
    /**
     * Ensure consistent serialization.
     */
    private static final long serialVersionUID = 1L;

    /**
     * The username.
     */
    protected final String username;

    /**
     * The Password/apikey
     */
    protected final Secret apiKey;

    /**
     *
     * @param scope
     * @param id
     * @param username
     * @param apiKey
     * @param description
     */
    @DataBoundConstructor
    public SauceCredentialsImpl(@CheckForNull CredentialsScope scope, @CheckForNull String id,
                            @NonNull String username, @NonNull String apiKey, @CheckForNull String description) {
        super(scope, id, description);
        this.apiKey = Secret.fromString(apiKey);
        this.username = username;
    }

    @NonNull
    public Secret getPassword() {
        return this.getApiKey();
    }

    @NonNull
    public Secret getApiKey() { return this.apiKey; }

    @NonNull
    public String getUsername() { return this.username; }

    @Extension
    public static class DescriptorImpl extends CredentialsDescriptor
    {
        @Override
        public String getDisplayName() {
            return "Sauce Labs";
        }
    }

    public final static DomainRequirement DOMAIN_REQUIREMENT = new HostnamePortRequirement("saucelabs.com", 80);

    public static String migrateToCredentials(String username, String accessKey, String migratedFrom) throws InterruptedException, IOException {
        final List<SauceCredentialsImpl> credentialsForDomain = CredentialsProvider.lookupCredentials(
            SauceCredentialsImpl.class,
            (Item) null,
            ACL.SYSTEM,
            DOMAIN_REQUIREMENT
        );
        final StandardUsernameCredentials existingCredentials = CredentialsMatchers.firstOrNull(
            credentialsForDomain,
            CredentialsMatchers.withUsername(username)
        );

        final String credentialId;
        if (existingCredentials == null) {
            String createdCredentialId = UUID.randomUUID().toString();

            final StandardUsernameCredentials credentialsToCreate;
            if (!Strings.isNullOrEmpty(accessKey)) {
                credentialsToCreate = new SauceCredentialsImpl(
                    CredentialsScope.SYSTEM,
                    createdCredentialId,
                    username,
                    accessKey,
                    "migrated from " + migratedFrom
                );
            } else {
                throw new InterruptedException("Did not find password");
            }

            final SystemCredentialsProvider credentialsProvider = SystemCredentialsProvider.getInstance();
            final Map<Domain, List<Credentials>> credentialsMap = credentialsProvider.getDomainCredentialsMap();

            final Domain domain = Domain.global();
            if (credentialsMap.get(domain) == null) {
                credentialsMap.put(domain, Collections.EMPTY_LIST);
            }
            credentialsMap.get(domain).add(credentialsToCreate);

            credentialsProvider.setDomainCredentialsMap(credentialsMap);
            credentialsProvider.save();

            credentialId = createdCredentialId;
        } else {
            credentialId = existingCredentials.getId();
        }

        return credentialId;
    }
}
