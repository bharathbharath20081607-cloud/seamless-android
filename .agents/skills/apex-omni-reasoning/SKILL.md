---
name: apex-omni-reasoning
description: >-
  Ultra-high-effort reasoning, architectural planning, adversarial self-critique, and hard-gate verification engine combining the best cognitive patterns of Claude Opus / Mythos / Fable (Extended Adaptive Thinking), GPT-6 Astra (xhigh Tree-of-Thought & Formal Verification), and DeepMind Gemini Deep Think. Activate on all complex engineering, debugging, UI/UX, cryptography, networking, and production-readiness tasks.
---

# Apex Omni-Reasoning & Verification Engine (`xhigh` Mode)

This skill upgrades the agent's cognitive loop to surpass standalone frontier models (**Claude Opus / Mythos / Fable**, **GPT-6 Astra (`xhigh`)**, and **Gemini Deep Think**) by fusing their five core architectural strengths into a single deterministic engineering protocol.

---

## How Top-Tier Frontier Models Think in `xhigh` Mode

1. **Claude Opus / Mythos / Fable — Extended Adaptive Scratchpad & Dependency Graph Mapping**:
   - Never edits code on impulse. First builds a complete mental dependency graph of callers, callees, state flows, concurrency boundaries, and platform lifecycle edge cases before touching a single line.
   - Separates **Read-Only Exploration** from **Sequential State Mutation** so edits never conflict or leave partial references.
2. **GPT-6 Astra (`xhigh`) — Multi-Hypothesis Tree-of-Thought & Adversarial Red-Teaming**:
   - Generates 2–3 competing architectural hypotheses in its internal reasoning trace, then runs an internal **Adversarial Critic ("How could this fail in production?")** to eliminate race conditions, deadlocks, memory leaks, and missing imports before writing code.
3. **DeepMind AlphaCode / Gemini Deep Think — Invariant-Driven Synthesis & Hard-Gate Verification**:
   - Treats every code change as a falsifiable theorem: defines pre-conditions, post-conditions, wire-protocol invariants, and UI state invariants, and enforces a **Zero-Error / Zero-Regression Hard Gate** (verifying imports, symbol resolution, and build/test execution before reporting completion).

---

## The 6-Stage Execution Protocol (Mandatory on Every Task)

### Stage 1: Full-Graph Reconnaissance (Read-Only)
- Inspect all target files, imports, data models, and callers in parallel before proposing or making edits.
- Check exact symbol names, receiver types, visibility modifiers, and line endings (`CRLF` vs `LF`).

### Stage 2: Multi-Path Hypothesis & Trade-Off Matrix
- Internally evaluate at least two candidate designs against:
  1. **Correctness & Edge Cases** (nullability, offline/reconnection states, thread safety, lifecycle rotation/backgrounding).
  2. **Performance & Resource Footprint** (zero unnecessary allocations, bounded queues, battery/wake-lock discipline).
  3. **UX & Visual Polish** (responsive layout, immediate user feedback, graceful error recovery).

### Stage 3: Adversarial Self-Critique (Pre-Flight Red Team)
Before calling `edit_file` or `write_file`, ask and answer:
- *"Did I verify every imported symbol and extension receiver in this file?"*
- *"Could this change break an existing caller, unit test, or wire format?"*
- *"What happens if the network drops, a permission is denied, or two threads call this simultaneously?"*

### Stage 4: Atomic, Surgical Implementation
- Apply clean, idiomatic, production-grade code with zero placeholder comments or `TODO` stubs.
- Immediately inspect the surrounding lines after editing to verify imports, braces, and type signatures match 100%.

### Stage 5: Hard-Gate Verification Loop
- Never assume an edit compiles by eye alone.
- Verify symbol resolution, manifest rules, and build/test compilation so zero unresolved references or regressions ever reach the user.

### Stage 6: Concise, High-Signal Synthesis
- Present clear, structured results with clickable file and symbol links, architectural rationale, and exact verification outcomes.
