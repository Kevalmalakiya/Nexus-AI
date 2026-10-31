# Architecture
Phase 1 is one Gradle module (`:app`) with package-per-module boundaries
(ai, personality, security, ui). Packages split into Gradle modules
(ai_engine, voice_engine, memory_engine, device_control, screen_vision,
termux_bridge, coding_agent, security) as each phase lands.
Rules: UI never calls HTTP directly; AI goes through AiProvider; secrets only
via SecureStore; AI planning is separate from any command execution.
