# Koog Subgraph Trace Hierarchy

We switched the custom agent strategies to use Koog subgraphs instead of keeping all work directly under one top-level strategy graph.

## Why

Koog traces are naturally graph-oriented. Without subgraphs, Phoenix shows a valid hierarchy, but most `node ...` spans sit as siblings under one `strategy ...` span, which makes the trace feel flatter than a chat-turn tree.

Koog subgraphs are the idiomatic way to break a larger workflow into named phases while keeping the strategy inside Koog's own DSL.

## What changed

The chicken and breed strategies now group work into named phases such as:

- `setup_run`
- `llm_turn`
- `execute_tools_turn`
- `tool_result_turn`
- `request_save_*_turn`

The goal is to make the trace tree read more like workflow phases rather than one long list of sibling nodes.

## Expected effect in Phoenix

Instead of mostly seeing:

```text
strategy
- node call_llm
- node execute_tool
- node capture_tool_result
- node send_tool_result
```

we want more visible grouping like:

```text
strategy
|- setup_run
|- llm_turn
|  \- chat ...
|- execute_tools_turn
|  \- execute_tool ...
|- tool_result_turn
|  \- chat ...
```

This stays idiomatic Koog. No custom telemetry shim was added.

## Sources

- Koog subgraphs overview: https://docs.koog.ai/subgraphs-overview/
- Koog custom subgraphs guide: https://docs.koog.ai/custom-subgraphs/
- Koog source for subgraph execution and context handling:
  https://raw.githubusercontent.com/JetBrains/koog/1.0.0/agents/agents-core/src/commonMain/kotlin/ai/koog/agents/core/agent/entity/AIAgentSubgraph.kt
