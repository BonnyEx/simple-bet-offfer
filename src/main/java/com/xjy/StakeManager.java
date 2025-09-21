package com.xjy;


import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stake管理器：负责Stake的存储（保留最高值）和Top20查询
 */
public class StakeManager {
    private static final int TOP_LIMIT = 20; // 最多返回Top20

    // 双层ConcurrentHashMap：投注项ID → (客户ID → 最高Stake)
    private final Map<Integer, Map<Integer, Integer>> offerStakes = new ConcurrentHashMap<>();

    /**
     * 保存Stake（同一客户同一投注项保留最高值）
     * @param betOfferId 投注项ID
     * @param customerId 客户ID
     * @param stake 本次提交的Stake
     */
    public void saveStake(int betOfferId, int customerId, int stake) {
        // 1. 原子获取或创建投注项的Stake映射
        Map<Integer, Integer> customerStakes = offerStakes.computeIfAbsent(
                betOfferId, v -> new ConcurrentHashMap<>()
        );

        // 2. 原子更新为最大值（避免竞态条件）
        customerStakes.compute(
                customerId,
                (k, existing) -> existing == null ? stake : Math.max(existing, stake)
        );
    }

    /**
     * 查询投注项的Top20最高Stake（按Stake降序，每个客户仅1条）
     * @param betOfferId 投注项ID
     * @return Top20 Stake列表（客户ID→Stake）
     */
    public List<Map.Entry<Integer, Integer>> getTopStakes(int betOfferId) {
        // 1. 获取该投注项的所有Stake（无数据则返回空列表）
        Map<Integer, Integer> customerStakes = offerStakes.get(betOfferId);
        if (customerStakes == null || customerStakes.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 转换为列表并按Stake降序排序
        List<Map.Entry<Integer, Integer>> sorted = new ArrayList<>(customerStakes.entrySet());
        sorted.sort((e1, e2) -> Integer.compare(e2.getValue(), e1.getValue()));

        // 3. 截取Top20
        int end = Math.min(sorted.size(), TOP_LIMIT);
        return sorted.subList(0, end);
    }

    /**
     * 将额度列表格式化为字符串
     * @param entryList 投注额度列表
     * @return 符合接口返回格式的字符串
     */
    public String formatEntryList (List<Map.Entry<Integer, Integer>> entryList) {
        // 处理空列表情况
        if (entryList == null || entryList.isEmpty ()) {
            return "";
        }
        // 拼接结果字符串
        StringJoiner joiner = new StringJoiner (",");
        for (Map.Entry<Integer, Integer> entry : entryList) {
            Integer key = entry.getKey ();
            Integer value = entry.getValue ();
            // 如果不允许 null，可以添加非空校验并抛出异常
        if (key == null || value == null) {
            throw new IllegalArgumentException ("Customer id cannot be null");
         }
            joiner.add(key + "=" + value);
        }
        return joiner.toString();
    }
}
