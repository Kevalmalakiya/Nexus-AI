package com.nexus.ai

import com.nexus.ai.personality.PersonalityEngine
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalityEngineTest {
    @Test fun defaultRatioIs70_30() {
        val p = PersonalityEngine.systemPrompt()
        assertTrue(p.contains("70% Angel") && p.contains("30% Devil"))
    }
    @Test fun ratioIsClamped() {
        assertTrue(PersonalityEngine.systemPrompt(500).contains("100% Angel"))
    }
}
