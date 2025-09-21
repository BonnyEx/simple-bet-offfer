package com.xjy.util;

import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * HTTP通用工具类：处理响应发送、请求体读取
 */
public class HttpUtils {
    // 发送错误响应（含状态码和提示信息）
    public static void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        byte[] responseBytes = message.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        } finally {
            exchange.close();
        }
    }

    // 发送成功响应（含响应体）
    public static void sendSuccess(HttpExchange exchange, String responseBody) throws IOException {
        byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(200, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        } finally {
            exchange.close();
        }
    }

    // 发送无内容成功响应（204）
    public static void sendNoContent(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(204, -1); // -1表示无响应体
        exchange.close();
    }
    public static String readRequestBody(HttpExchange exchange)  {
        try (InputStream inputStream = exchange.getRequestBody()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    // 读取Form格式请求体参数（trim去除首尾空格）
    public static Map<String, String> readRequestBodyParameters(HttpExchange exchange) {
        try (InputStream inputStream = exchange.getRequestBody()) {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8)
            );
            StringBuilder requestBody = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                requestBody.append(line);
            }

            // 2. 解析表单格式参数（key=value&key=value）
            return parseFormParams(requestBody.toString());
        } catch (Exception e) {
            System.err.println(e.getMessage());
            return null;
        }
    }

    // 读取url链接中的参数
    public static Map<String, String> readParamMapFromQueryString(HttpExchange exchange) {
        // 1. 获取请求的 URI（包含路径和查询参数）
        URI requestUri = exchange.getRequestURI();

        // 2. 提取查询字符串（如 "param1=val1&param2=val2"，若没有则为 null）
        String query = requestUri.getQuery();

        return parseFormParams(query);
    }

    // 解析表单格式的参数字符串为 Map
    private static Map<String, String> parseFormParams(String body) {
        Map<String, String> params = new HashMap<>();
        if (body == null || body.isBlank()) {
            return params;
        }
        String[] pairs = body.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=", 2); // 最多分割为两部分（处理值中含=的情况）
            if (keyValue.length > 0) {
                String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
                String value = keyValue.length > 1
                        ? URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8) : "";
                params.put(key, value);
            }
        }
        return params;
    }
}
