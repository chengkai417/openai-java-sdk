package org.devlive.sdk.platform.google;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.devlive.sdk.common.DefaultClient;
import org.devlive.sdk.common.exception.ParamException;
import org.devlive.sdk.common.exception.RequestException;
import org.devlive.sdk.common.utils.ValidateUtils;
import org.devlive.sdk.openai.mixin.IgnoreUnknownMixin;
import org.devlive.sdk.openai.model.ProviderModel;
import org.devlive.sdk.openai.model.UrlModel;
import org.devlive.sdk.openai.utils.MultipartBodyUtils;
import org.devlive.sdk.openai.utils.ProviderUtils;
import org.devlive.sdk.platform.google.entity.ChatEntity;
import org.devlive.sdk.platform.google.interceptor.GoogleInterceptor;
import org.devlive.sdk.platform.google.model.GenerativeModel;
import org.devlive.sdk.platform.google.model.VersionModel;
import org.devlive.sdk.platform.google.response.ChatResponse;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.util.concurrent.TimeUnit;

@Slf4j
@Builder
public class GoogleClient
        extends DefaultClient
{
    private final ObjectMapper objectMapper = new ObjectMapper();

    private String apiKey;
    private String apiHost;
    private Integer timeout;
    private TimeUnit unit;
    private OkHttpClient client;
    private String model;
    private VersionModel version;
    private GoogleApi api;
    private EventSourceListener listener;

    private GoogleClient(GoogleClientBuilder builder)
    {
        boolean hasApiKey = StringUtils.isNotEmpty(builder.apiKey);
        if (!hasApiKey) {
            log.error("Invalid Google token");
            throw new ParamException("Invalid Google token");
        }

        if (ObjectUtils.isEmpty(builder.apiHost)) {
            builder.apiHost(null);
        }
        if (ObjectUtils.isEmpty(builder.timeout)) {
            builder.timeout(null);
        }
        if (ObjectUtils.isEmpty(builder.unit)) {
            builder.unit(null);
        }

        if (ObjectUtils.isEmpty(builder.version)) {
            builder.version(VersionModel.V1BETA);
        }
        this.version = builder.version;

        if (ObjectUtils.isEmpty(builder.model)) {
            builder.model(GenerativeModel.GEMINI_PRO);
        }
        this.model = builder.model;

        if (ObjectUtils.isEmpty(builder.listener)) {
            builder.listener(null);
        }
        super.listener = builder.listener;
        this.listener = builder.listener;

        if (ObjectUtils.isEmpty(builder.client)) {
            builder.client(null);
        }

        super.client = builder.client;
        this.client = builder.client;
        super.apiHost = builder.apiHost;
        this.apiHost = builder.apiHost;
        super.provider = ProviderModel.GOOGLE_GEMINI;

        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        this.api = new Retrofit.Builder()
                .baseUrl(builder.apiHost)
                .client(builder.client)
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .addConverterFactory(JacksonConverterFactory.create(objectMapper))
                .build()
                .create(GoogleApi.class);
    }

    public ChatResponse createChatCompletions(ChatEntity configure)
    {
        String url = ProviderUtils.getUrl(provider, UrlModel.FETCH_CHAT_COMPLETIONS);
        if (ObjectUtils.isNotEmpty(this.listener)) {
            this.createEventSource(url, configure);
            return null;
        }

        return this.api.fetchChatCompletions(url, configure)
                .blockingGet();
    }

    private ObjectMapper createObjectMapper()
    {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.addMixIn(Object.class, IgnoreUnknownMixin.class);
        return objectMapper;
    }

    private void createEventSource(String url, Object configure)
    {
        try {
            EventSource.Factory factory = EventSources.createFactory(this.client);
            ObjectMapper mapper = this.createObjectMapper();
            Request request = new Request.Builder()
                    .url(String.join("/", this.apiHost, url))
                    .post(RequestBody.create(MultipartBodyUtils.JSON, mapper.writeValueAsString(configure)))
                    .build();
            factory.newEventSource(request, this.listener);
        }
        catch (Exception e) {
            throw new RequestException(String.format("Failed to create event source: %s", e.getMessage()));
        }
    }

    public static class GoogleClientBuilder
    {
        public GoogleClientBuilder apiKey(String apiKey)
        {
            this.apiKey = apiKey;
            return this;
        }

        public GoogleClientBuilder apiHost(String apiHost)
        {
            this.apiHost = ValidateUtils.validateHost(apiHost, "https://generativelanguage.googleapis.com");
            return this;
        }

        public GoogleClientBuilder timeout(Integer timeout)
        {
            if (ObjectUtils.isEmpty(timeout)) {
                timeout = 30;
            }
            this.timeout = timeout;
            return this;
        }

        public GoogleClientBuilder unit(TimeUnit unit)
        {
            if (ObjectUtils.isEmpty(unit)) {
                unit = TimeUnit.SECONDS;
            }
            this.unit = unit;
            return this;
        }

        public GoogleClientBuilder client(OkHttpClient client)
        {
            if (ObjectUtils.isEmpty(client)) {
                log.debug("No client specified, creating default client");
                client = new OkHttpClient.Builder()
                        .connectTimeout(this.timeout, this.unit)
                        .writeTimeout(this.timeout, this.unit)
                        .readTimeout(this.timeout, this.unit)
                        .callTimeout(this.timeout, this.unit)
                        .build();
            }
            GoogleInterceptor interceptor = new GoogleInterceptor();
            interceptor.setApiKey(apiKey);
            interceptor.setVersion(version);
            interceptor.setModel(model);
            if (listener != null) {
                interceptor.setStream(true);
            }
            client = client.newBuilder()
                    .addInterceptor(interceptor)
                    .build();
            this.client = client;
            return this;
        }

        public GoogleClientBuilder model(GenerativeModel model)
        {
            this.model = model.getName();
            return this;
        }

        public GoogleClient build()
        {
            return new GoogleClient(this);
        }
    }
}
