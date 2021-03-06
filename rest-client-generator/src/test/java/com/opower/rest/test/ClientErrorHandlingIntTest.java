package com.opower.rest.test;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.google.common.collect.ImmutableList;
import com.opower.rest.client.generator.core.*;
import com.opower.rest.client.generator.executors.ApacheHttpClient4Executor;
import com.opower.rest.client.generator.util.HttpResponseCodes;
import com.opower.rest.test.jetty.JettyRule;
import com.opower.rest.test.resource.FrobResource;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.hamcrest.Matchers.containsString;

/**
 * @author chris.phillips
 */
public class ClientErrorHandlingIntTest {

    private static int PORT = 7999;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .setDateFormat(new ISO8601DateFormat())
            .registerModule(new GuavaModule())
            .registerModule(new JodaModule());
    private static final JacksonJsonProvider JACKSON_JSON_PROVIDER = new JacksonJsonProvider(OBJECT_MAPPER)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            // enabling this feature to make deserialization fail
            .configure(DeserializationFeature.UNWRAP_ROOT_VALUE, true);


    @ClassRule
    public static final JettyRule JETTY_RULE =
            new JettyRule(PORT, ClientErrorHandlingIntTest.class.getResource("/jersey/1/web.xml").toString());

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private FrobResource frobResource = new Client.Builder<>(new ResourceInterface<>(FrobResource.class),
            new SimpleUriProvider(String.format("http://localhost:%s/", PORT)))
            .clientErrorInterceptors(ImmutableList.<ClientErrorInterceptor>of(new TestClientErrorInterceptor()))
            .executor(new ApacheHttpClient4Executor()).registerProviderInstance(JACKSON_JSON_PROVIDER).build();

    @Test
    public void nonClientResponseFailureThrowsOriginalException() {
        expectedException.expect(RuntimeException.class);
        expectedException.expectMessage(containsString("JsonMappingException"));
        frobResource.frobJsonError();
    }

    @Test
    public void clientResponseFailureIsHandledByErrorInterceptor() {
        expectedException.expect(StatusCodeMatcher.ResponseError.class);
        expectedException.expect(new StatusCodeMatcher(HttpResponseCodes.SC_INTERNAL_SERVER_ERROR));
        frobResource.frobErrorResponse(HttpResponseCodes.SC_INTERNAL_SERVER_ERROR);
    }


    private class TestClientErrorInterceptor implements ClientErrorInterceptor {

        @Override
        public void handle(ClientResponse response) throws RuntimeException {
            throw new StatusCodeMatcher.ResponseError(response);
        }
    }
}
