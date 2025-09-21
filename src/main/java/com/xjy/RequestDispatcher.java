package com.xjy;

import com.xjy.util.HttpUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 请求分发器：匹配URL和方法，路由到对应Handler
 */
public class RequestDispatcher implements HttpHandler {
    private final SessionManager sessionManager;
    private final StakeManager stakeManager;

    // 接口URL正则表达式
    private static final Pattern SESSION_PATTERN = Pattern.compile("^/(\\d+)/session$"); // 获取会话 GET /{customerId}/session
    private static final Pattern STAKE_PATTERN = Pattern.compile("^/(\\d+)/stake$");     // 投注  POST /{betOfferId}/stake
    private static final Pattern HIGH_STAKES_PATTERN = Pattern.compile("^/(\\d+)/highstakes$"); // 获取最高投注额度列表 GET /{betOfferId}/highstakes

    public RequestDispatcher(SessionManager sessionManager, StakeManager stakeManager) {
        this.sessionManager = sessionManager;
        this.stakeManager = stakeManager;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String uri = exchange.getRequestURI().getPath();


        try {
            // 匹配Get Session接口
            Matcher sessionMatcher = SESSION_PATTERN.matcher(uri);
            if (method.equalsIgnoreCase("GET") && sessionMatcher.matches()) {
                int customerId = Integer.parseInt(sessionMatcher.group(1));
                String sessionKey = sessionManager.getOrCreateSession(customerId);
                HttpUtils.sendSuccess(exchange, sessionKey);
                return;
            }

            // 匹配Post Stake接口
            Matcher stakeMatcher = STAKE_PATTERN.matcher(uri);
            if (method.equalsIgnoreCase("POST") && stakeMatcher.matches()) {
                int betOfferId = Integer.parseInt(stakeMatcher.group(1));
                String stakeStr = HttpUtils.readRequestBody(exchange);
                int stake;
                try {
                    assert stakeStr != null;
                    stake = Integer.parseInt(stakeStr);
                    if (stake <= 0) {
                        HttpUtils.sendError(exchange, 400, "Invalid stake value. Stake must be a positive integer");
                        return;
                    }
                } catch (Exception e) {
                    HttpUtils.sendError(exchange, 400, "Invalid stake value. Stake must be a positive integer");
                    return;
                }
                Map<String, String> requestBodyParamMap = HttpUtils.readParamMapFromQueryString(exchange);
                String sessionKey = requestBodyParamMap.getOrDefault("sessionKey", "");
                if (sessionKey == null || sessionKey.isBlank()) {
                    HttpUtils.sendError(exchange, 401, "Unauthorized. Invalid sessionId");
                } else {
                    Integer customerId = sessionManager.validateSession(sessionKey);
                    if (customerId == null) {
                        HttpUtils.sendError(exchange, 401, "Unauthorized. Invalid sessionId");
                    }
                    stakeManager.saveStake(betOfferId, customerId, stake);
                    HttpUtils.sendNoContent(exchange);
                }

                return;
            }

            // 匹配Get HighStakes接口
            Matcher highStakesMatcher = HIGH_STAKES_PATTERN.matcher(uri);
            if (method.equalsIgnoreCase("GET") && highStakesMatcher.matches()) {
                int betOfferId = Integer.parseInt(highStakesMatcher.group(1));
                String highStakeStr = stakeManager.formatEntryList(stakeManager.getTopStakes(betOfferId));
                if (highStakeStr == null || highStakeStr.isBlank()) {
                    HttpUtils.sendSuccess(exchange, "No valid stake found in this bet offer");
                    return;
                }
                HttpUtils.sendSuccess(exchange, highStakeStr);
                return;
            }

            // 未匹配到接口（404）
            HttpUtils.sendError(exchange, 404, "Resource not found: " + uri);

        } catch (NumberFormatException e) {
            // 参数非整数（400）
            HttpUtils.sendError(exchange, 400, "Invalid numeric parameter: " + e.getMessage());
        } catch (Exception e) {
            // 其他异常（500）
            HttpUtils.sendError(exchange, 500, "Internal server error: " + e.getMessage());
        }
    }



}
