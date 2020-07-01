package org.apereo.cas.syncope.authentication;

import org.apereo.cas.authentication.AuthenticationHandler;
import org.apereo.cas.authentication.CoreAuthenticationTestUtils;
import org.apereo.cas.authentication.exceptions.AccountDisabledException;
import org.apereo.cas.authentication.exceptions.AccountPasswordMustChangeException;
import org.apereo.cas.config.CasCoreAuthenticationConfiguration;
import org.apereo.cas.config.CasCoreAuthenticationPrincipalConfiguration;
import org.apereo.cas.config.CasCoreAuthenticationSupportConfiguration;
import org.apereo.cas.config.CasCoreConfiguration;
import org.apereo.cas.config.CasCoreHttpConfiguration;
import org.apereo.cas.config.CasCoreNotificationsConfiguration;
import org.apereo.cas.config.CasCoreServicesConfiguration;
import org.apereo.cas.config.CasCoreTicketCatalogConfiguration;
import org.apereo.cas.config.CasCoreTicketIdGeneratorsConfiguration;
import org.apereo.cas.config.CasCoreTicketsConfiguration;
import org.apereo.cas.config.CasCoreUtilConfiguration;
import org.apereo.cas.config.CasCoreWebConfiguration;
import org.apereo.cas.config.CasPersonDirectoryTestConfiguration;
import org.apereo.cas.config.SyncopeAuthenticationConfiguration;
import org.apereo.cas.config.support.CasWebApplicationServiceFactoryConfiguration;
import org.apereo.cas.logout.config.CasCoreLogoutConfiguration;
import org.apereo.cas.util.MockWebServer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Cleanup;
import lombok.SneakyThrows;
import lombok.val;
import org.apache.syncope.common.lib.to.MembershipTO;
import org.apache.syncope.common.lib.to.RelationshipTO;
import org.apache.syncope.common.lib.to.UserTO;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This is {@link SyncopeAuthenticationHandlerTests}.
 *
 * @author Misagh Moayyed
 * @since 5.3.0
 */
@SuppressWarnings("unused")
@SpringBootTest(classes = {
    RefreshAutoConfiguration.class,
    MailSenderAutoConfiguration.class,
    SyncopeAuthenticationConfiguration.class,
    CasCoreServicesConfiguration.class,
    CasCoreAuthenticationConfiguration.class,
    CasCoreAuthenticationSupportConfiguration.class,
    CasCoreAuthenticationPrincipalConfiguration.class,
    CasCoreHttpConfiguration.class,
    CasCoreWebConfiguration.class,
    CasCoreUtilConfiguration.class,
    CasCoreNotificationsConfiguration.class,
    CasCoreTicketsConfiguration.class,
    CasCoreTicketCatalogConfiguration.class,
    CasCoreTicketIdGeneratorsConfiguration.class,
    CasWebApplicationServiceFactoryConfiguration.class,
    CasCoreLogoutConfiguration.class,
    CasCoreConfiguration.class,
    CasPersonDirectoryTestConfiguration.class
},
    properties = "cas.authn.syncope.url=http://localhost:8095")
@ResourceLock("Syncope")
@Tag("Simple")
public class SyncopeAuthenticationHandlerTests {
    private static final ObjectMapper MAPPER = new IgnoringJaxbModuleJacksonObjectMapper().findAndRegisterModules();

    @Autowired
    @Qualifier("syncopeAuthenticationHandler")
    private AuthenticationHandler syncopeAuthenticationHandler;

    @Test
    @SuppressWarnings("JdkObsolete")
    public void verifyHandlerPasses() {
        val user = new UserTO();
        user.setUsername("admin");
        user.setSecurityQuestion("SecurityQuestion");
        user.setCreationDate(new Date());
        user.setChangePwdDate(new Date());
        user.getDynRoles().add("Role1");
        user.getMemberships().add(new MembershipTO().setGroupName("GroupKey"));
        user.getDynMemberships().add(new MembershipTO().setGroupName("GroupKey"));
        user.getRelationships().add(new RelationshipTO().setType("Type"));

        @Cleanup("stop")
        val webserver = startMockSever(user);
        assertDoesNotThrow(() ->
            syncopeAuthenticationHandler.authenticate(
                CoreAuthenticationTestUtils.getCredentialsWithDifferentUsernameAndPassword("admin", "password")));
    }

    @Test
    public void verifyHandlerMustChangePassword() {
        val user = new UserTO();
        user.setUsername("admin");
        user.setMustChangePassword(true);
        @Cleanup("stop")
        val webserver = startMockSever(user);

        assertThrows(AccountPasswordMustChangeException.class,
            () -> syncopeAuthenticationHandler.authenticate(
                CoreAuthenticationTestUtils.getCredentialsWithDifferentUsernameAndPassword("admin", "password")));
    }

    @Test
    public void verifyHandlerSuspended() {
        val user = new UserTO();
        user.setUsername("admin");
        user.setSuspended(true);
        @Cleanup("stop")
        val webserver = startMockSever(user);

        assertThrows(AccountDisabledException.class,
            () -> syncopeAuthenticationHandler.authenticate(
                CoreAuthenticationTestUtils.getCredentialsWithDifferentUsernameAndPassword("admin", "password")));
    }

    @SneakyThrows
    private static MockWebServer startMockSever(final UserTO user) {
        val data = MAPPER.writeValueAsString(user);
        val webServer = new MockWebServer(8095,
            new ByteArrayResource(data.getBytes(StandardCharsets.UTF_8), "REST Output"),
            MediaType.APPLICATION_JSON_VALUE);
        webServer.start();
        return webServer;
    }
}
