package org.discordbots.api.client.impl;

import com.google.gson.Gson;
import okhttp3.*;
import org.discordbots.api.client.DiscordBotListAPI;
import org.discordbots.api.client.entity.*;
import org.discordbots.api.client.io.DefaultResponseTransformer;
import org.discordbots.api.client.io.ResponseTransformer;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

public class DiscordBotListAPIImpl implements DiscordBotListAPI {

    private static final HttpUrl baseUrl = new HttpUrl.Builder()
            .scheme("https")
            .host("discordbots.org")
            .addPathSegment("api")
            .build();

    private final OkHttpClient httpClient;
    private final Gson gson;

    private final String token, botId;

    public DiscordBotListAPIImpl(String token, String botId, Gson gson, OkHttpClient httpClient) {
        this.token = token;
        this.botId = botId;
        this.gson = gson;
        this.httpClient = httpClient.newBuilder()
                .addInterceptor((chain) -> {
                    Request req = chain.request().newBuilder()
                            .addHeader("Authorization", this.token)
                            .build();
                    return chain.proceed(req);
                })
                .build();
    }

    public CompletionStage<Void> setStats(int shardId, int shardTotal, int serverCount) {
        JSONObject json = new JSONObject()
                .put("shard_id", shardId)
                .put("shard_total", shardTotal)
                .put("server_count", serverCount);

        return setStats(json);
    }

    public CompletionStage<Void> setStats(List<Integer> shardServerCounts) {
        JSONObject json = new JSONObject()
                .put("shards", shardServerCounts);

        return setStats(json);
    }

    public CompletionStage<Void> setStats(int serverCount) {
        JSONObject json = new JSONObject()
                .put("server_count", serverCount);

        return setStats(json);
    }

    private CompletionStage<Void> setStats(JSONObject jsonBody) {
        HttpUrl url = baseUrl.newBuilder()
                .addPathSegment("bots")
                .addPathSegment(botId)
                .addPathSegment("stats")
                .build();

        return post(url, jsonBody, Void.class);
    }

    public CompletionStage<BotStats> getStats(String botId) {
        HttpUrl url = baseUrl.newBuilder()
                .addPathSegment("bots")
                .addPathSegment(botId)
                .addPathSegment("stats")
                .build();

        return get(url, BotStats.class);
    }

    public CompletionStage<SimpleUser[]> getVoters(String botId) {
        HttpUrl url = baseUrl.newBuilder()
                .addPathSegment("bots")
                .addPathSegment(botId)
                .addPathSegment("votes")
                .build();

        return get(url, SimpleUser[].class);
    }

    public CompletionStage<Bot> getBot(String botId) {
        HttpUrl url = baseUrl.newBuilder()
                .addPathSegment("bots")
                .addPathSegment(botId)
                .build();

        return get(url, Bot.class);
    }

    public CompletionStage<BotResult> getBots(Map<String, String> search, int limit, int offset) {
        return getBots(search, limit, offset, null);
    }

    public CompletionStage<BotResult> getBots(Map<String, String> search, int limit, int offset, String sort) {
        return getBots(search, limit, offset, sort, null);
    }

    public CompletionStage<BotResult> getBots(Map<String, String> search, int limit, int offset, String sort, List<String> fields) {
        // DBL search uses this format: field1: value1 field2: value2
        String searchString = search.entrySet().stream()
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .collect(Collectors.joining(" "));

        HttpUrl.Builder urlBuilder = baseUrl.newBuilder()
                .addPathSegment("bots")
                .addQueryParameter("search", searchString)
                .addQueryParameter("limit", String.valueOf(limit))
                .addQueryParameter("offset", String.valueOf(offset));

        if(sort != null) {
            urlBuilder.addQueryParameter("sort", sort);
        }

        if(fields != null) {
            String fieldsString = fields.stream()
                    .collect(Collectors.joining(" "));

            urlBuilder.addQueryParameter("fields", fieldsString);
        }

        return get(urlBuilder.build(), BotResult.class);
    }

    public CompletionStage<User> getUser(String userId) {
        HttpUrl url = baseUrl.newBuilder()
                .addPathSegment("users")
                .addPathSegment(userId)
                .build();

        return get(url, User.class);
    }

    public CompletionStage<Boolean> hasVoted(String userId) {
        HttpUrl url = baseUrl.newBuilder()
                .addPathSegment("bots")
                .addPathSegment(botId)
                .addPathSegment("check")
                .addQueryParameter("userId", userId)
                .build();

        return get(url, (resp) -> {
            JSONObject json = new JSONObject(resp.body().string());
            return json.getInt("voted") == 1;
        });
    }

    private <E> CompletionStage<E> get(HttpUrl url, Class<E> clazz) {
        return get(url, new DefaultResponseTransformer<>(clazz, gson));
    }

    private <E> CompletionStage<E> get(HttpUrl url, ResponseTransformer<E> responseTransformer) {
        Request req = new Request.Builder()
                .get()
                .url(url)
                .build();

        return execute(req, responseTransformer);
    }

    // The class provided in this is kinda unneeded because the only thing ever given to it
    // is Void, but I wanted to make it expandable (maybe some post methods will return objects
    // in the future)
    private <E> CompletionStage<E> post(HttpUrl url, JSONObject jsonBody, Class<E> clazz) {
        return post(url, jsonBody, new DefaultResponseTransformer<>(clazz, gson));
    }

    private <E> CompletionStage<E> post(HttpUrl url, JSONObject jsonBody, ResponseTransformer<E> responseTransformer) {
        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create(mediaType, jsonBody.toString());

        Request req = new Request.Builder()
                .post(body)
                .url(url)
                .build();

        return execute(req, responseTransformer);
    }

    private <E> CompletionStage<E> execute(Request request, ResponseTransformer<E> responseTransformer) {
        Call call = httpClient.newCall(request);

        final CompletableFuture<E> future = new CompletableFuture<>();

        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                System.out.println("fail");
                future.completeExceptionally(e);
            }

            @Override
            public void onResponse(Call call, Response response) {
                try {
                    E transformed = responseTransformer.transform(response);
                    future.complete(transformed);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }
        });

        return future;
    }

}
