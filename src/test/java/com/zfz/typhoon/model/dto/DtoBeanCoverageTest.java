package com.zfz.typhoon.model.dto;

import com.zfz.typhoon.testsupport.BeanSweeper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code com.zfz.typhoon.model.dto} 请求 DTO 的反射扫描测试。
 *
 * <p>本包目前只有 {@link SandboxRequest} 一个类。
 * 带条件的便捷方法 {@code nameOrDefault()} 另有针对性用例，
 * 见 {@link SandboxRequestTest}。
 *
 * @author 追风者项目组
 */
@DisplayName("model.dto 包 · 请求 DTO 反射扫描")
class DtoBeanCoverageTest {

    @Test
    @DisplayName("SandboxRequest：反射扫描构造与全部读写属性")
    void sweepSandboxRequest() {
        int covered = BeanSweeper.sweep(SandboxRequest.class);

        assertTrue(covered >= 0, "SandboxRequest 无可用无参构造");
        // 该 DTO 共 7 个字段：name / typhoonId / type / landingOffset /
        // speedFactor / intensityDelta / windScale / shared —— 至少 6 个可回读
        assertTrue(covered >= 6, "成功回读的属性过少：" + covered);
    }
}
