package xlike.top.audio.utils;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * @author xlike
 */
public class OkHttpUtils {

    private static final Logger logger = LoggerFactory.getLogger(OkHttpUtils.class);
    private static final int DEFAULT_READ_TIMEOUT = 300;
    private static final int DEFAULT_CONNECT_TIMEOUT = 300;

    static OkHttpClient getClient(Integer connectTimeout, Integer readTimeout, Proxy proxy) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.connectTimeout(Objects.requireNonNullElse(connectTimeout, DEFAULT_CONNECT_TIMEOUT), TimeUnit.SECONDS);
        builder.readTimeout(Objects.requireNonNullElse(readTimeout, DEFAULT_READ_TIMEOUT), TimeUnit.SECONDS);
        if (proxy != null) {
            builder.proxy(proxy);
            logger.info("使用代理服务器 {}:{}", proxy.address().toString(), ((InetSocketAddress) proxy.address()).getPort());
        }
        return builder.build();
    }

    private static Response sendRequest(String url, String method, Map<String, String> headers, RequestBody body,
                                        Integer connectTimeout, Integer readTimeout, Proxy proxy) throws IOException {
        logger.info("准备发送 {} 请求到 URL: {}", method, url);
        Request.Builder requestBuilder = new Request.Builder().url(url).method(method, body);
        if (headers != null) {
            for (Map.Entry<String, String> header : headers.entrySet()) {
                requestBuilder.addHeader(header.getKey(), header.getValue());
                logger.debug("添加请求头：{} = {}", header.getKey(), header.getValue());
            }
        }

        Request request = requestBuilder.build();
        OkHttpClient client = getClient(connectTimeout, readTimeout, proxy);
        long startTimeMillis = System.currentTimeMillis();
        LocalDateTime startTime = LocalDateTime.now();
        DateTimeFormatter beginFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        try {
            logger.info("请求开始，当前时间: {}", startTime.format(beginFormatter));
            Response response = client.newCall(request).execute();
            long endTimeMillis = System.currentTimeMillis();
            LocalDateTime endTime = LocalDateTime.now();
            DateTimeFormatter endFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            logger.info("请求结束，当前时间: {} ,耗时 {} 毫秒，返回状态码: {}",
                    endTime.format(endFormatter), (endTimeMillis - startTimeMillis), response.code());
            return response;
        } catch (IOException e) {
            long errorTimeMillis = System.currentTimeMillis();
            logger.error("请求失败，耗时 {} 毫秒，错误信息: {}", (errorTimeMillis - startTimeMillis), e.getMessage());
            throw e;
        }
    }

    // 添加 multipart 文件上传支持
    public static RequestBody createMultipartRequestBody(Map<String, String> formData, File file, String fileFieldName) {
        MultipartBody.Builder builder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM);

        // 添加表单数据
        if (formData != null) {
            for (Map.Entry<String, String> entry : formData.entrySet()) {
                builder.addFormDataPart(entry.getKey(), entry.getValue());
                logger.debug("添加表单字段：{} = {}", entry.getKey(), entry.getValue());
            }
        }

        // 添加文件
        if (file != null && fileFieldName != null) {
            builder.addFormDataPart(
                    fileFieldName,
                    file.getName(),
                    RequestBody.create(file, MediaType.parse("audio/mpeg"))
            );
            logger.info("添加上传文件：{}，路径：{}", file.getName(), file.getAbsolutePath());
        }

        return builder.build();
    }

    public static Response get(String url, Map<String, String> headers) throws IOException {
        return sendRequest(url, "GET", headers, null, null, null, null);
    }

    public static Response get(String url, Map<String, String> headers, Proxy proxy) throws IOException {
        return sendRequest(url, "GET", headers, null, null, null, proxy);
    }

    public static Response post(String url, Map<String, String> headers, RequestBody body) throws IOException {
        if(body == null) {
            body = RequestBody.create("", MediaType.get("application/json; charset=utf-8"));
        }
        return sendRequest(url, "POST", headers, body, null, null, null);
    }

    public static Response post(String url, Map<String, String> headers, RequestBody body, Proxy proxy) throws IOException {
        if(body == null) {
            body = RequestBody.create("", MediaType.get("application/json; charset=utf-8"));
        }
        return sendRequest(url, "POST", headers, body, null, null, proxy);
    }

    public static Response put(String url, Map<String, String> headers, RequestBody body) throws IOException {
        return sendRequest(url, "PUT", headers, body, null, null, null);
    }

    public static Response put(String url, Map<String, String> headers, RequestBody body, Proxy proxy) throws IOException {
        return sendRequest(url, "PUT", headers, body, null, null, proxy);
    }

    public static Response delete(String url, Map<String, String> headers, Proxy proxy) throws IOException {
        return sendRequest(url, "DELETE", headers, null, null, null, proxy);
    }

    public static Response deleteWithBody(String url, Map<String, String> headers, RequestBody body, Proxy proxy) throws IOException {
        return sendRequest(url, "DELETE", headers, body, null, null, proxy);
    }

    public static Proxy createProxy(String hostname, int port) {
        logger.info("创建代理，主机名: {}，端口: {}", hostname, port);
        return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(hostname, port));
    }

    public static RequestBody createJsonRequestBody(String json) {
        logger.info("创建JSON请求体: {}", json);
        return RequestBody.create(json, MediaType.get("application/json; charset=utf-8"));
    }

    public static RequestBody createFormRequestBody(Map<String, String> formData) {
        FormBody.Builder formBuilder = new FormBody.Builder();
        for (Map.Entry<String, String> entry : formData.entrySet()) {
            formBuilder.add(entry.getKey(), entry.getValue());
            logger.debug("添加表单字段：{} = {}", entry.getKey(), entry.getValue());
        }
        return formBuilder.build();
    }

    public static String getResponseBodyAsString(Response response) throws IOException {
        if (response.body() != null) {
            String responseBody = response.body().string();
            logger.debug("响应体内容: {}", responseBody);
            return responseBody;
        }
        return null;
    }

    /**
     * 从 Response 中获取指定名称的 Cookie 值
     *
     * @param response  HTTP 响应对象
     * @param cookieName  要获取的 Cookie 名称
     * @return 指定 Cookie 的值，如果未找到则返回 null
     */
    public static String getCookieFromResponse(Response response, String cookieName) {
        if (response == null || cookieName == null || cookieName.trim().isEmpty()) {
            logger.warn("Response 或 Cookie 名称为空，无法获取 Cookie");
            return null;
        }
        // 从响应头中获取所有 Set-Cookie 字段
        List<String> cookies = response.headers("Set-Cookie");
        if (cookies.isEmpty()) {
            logger.debug("响应中未找到任何 Set-Cookie 头");
            return null;
        }

        // 遍历所有 Cookie，查找指定名称的 Cookie
        for (String cookie : cookies) {
            String[] cookieParts = cookie.split(";");
            for (String part : cookieParts) {
                String[] keyValue = part.split("=", 2);
                if (keyValue.length == 2 && keyValue[0].trim().equals(cookieName)) {
                    String cookieValue = keyValue[1].trim();
                    logger.debug("找到 Cookie: {} = {}", cookieName, cookieValue);
                    return cookieValue;
                }
            }
        }

        logger.debug("未找到名为 {} 的 Cookie", cookieName);
        return null;
    }

    public static void closeResponse(Response response) {
        if (response != null && response.body() != null) {
            logger.info("关闭响应，URL: {}", response.request().url());
            response.close();
        }
    }
}