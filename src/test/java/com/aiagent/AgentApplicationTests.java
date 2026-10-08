package com.aiagent;

import com.aiagent.agent.Agent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AgentApplicationTests {

    @Autowired
    private Agent agent;

    @Autowired
    private com.aiagent.tools.Tools tools;

    @Test
    void contextLoads() {
    }

    @Test
    void testAgentChat() {
        String response = agent.chat("test-session", "Sistemdeki tüm ürünleri ve stok durumlarını özetle");
        org.assertj.core.api.Assertions.assertThat(response).isNotBlank();
        org.assertj.core.api.Assertions.assertThat(response).containsIgnoringCase("stok");
    }
}
