package com.xjy.util;

import java.util.HashMap;
import java.util.Map;

public class Base62Util {
    // Base62编码字符集: A-Z(26) + a-z(26) + 0-9(10)
    private static final String BASE62_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int BASE = 62;
    // 字符到数值的映射缓存
    private static final Map<Character, Integer> CHAR_TO_VALUE = new HashMap<>();

    // 静态初始化字符映射
    static {
        for (int i = 0; i < BASE62_CHARACTERS.length(); i++) {
            CHAR_TO_VALUE.put(BASE62_CHARACTERS.charAt(i), i);
        }
    }

    /**
     * 将Integer转换为Base62编码字符串
     * @param number 要转换的整数，必须是非负数
     * @return Base62编码字符串
     * @throws IllegalArgumentException 如果输入为负数
     */
    public static String encode(int number) {
        if (number < 0) {
            throw new IllegalArgumentException("只支持非负整数转换，输入值: " + number);
        }
        if (number == 0) {
            return String.valueOf(BASE62_CHARACTERS.charAt(0));
        }

        StringBuilder sb = new StringBuilder();
        while (number > 0) {
            int remainder = number % BASE;
            sb.append(BASE62_CHARACTERS.charAt(remainder));
            number = number / BASE;
        }

        // 反转得到正确结果（因为我们是从低位开始处理的）
        return sb.reverse().toString();
    }

    /**
     * 将Base62编码字符串转换为Integer
     * @param base62Str Base62编码字符串
     * @return 对应的整数
     * @throws IllegalArgumentException 如果输入包含无效字符或转换后超出Integer范围
     */
    public static int decode(String base62Str) {
        if (base62Str == null || base62Str.isEmpty()) {
            throw new IllegalArgumentException("Base62字符串不能为空");
        }

        long result = 0; // 使用long避免中间计算溢出
        for (int i = 0; i < base62Str.length(); i++) {
            char c = base62Str.charAt(i);
            Integer value = CHAR_TO_VALUE.get(c);

            if (value == null) {
                throw new IllegalArgumentException("无效的Base62字符: " + c);
            }

            // 计算: result = result * 62 + 当前字符值
            result = result * BASE + value;

            // 检查是否超出Integer范围
            if (result > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Base62字符串对应的数值超出Integer范围: " + base62Str);
            }
        }

        return (int) result;
    }
}
