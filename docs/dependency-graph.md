# Dependency Graph

Node types:
- External State (ES)
- External State Group (EG)
- Internal State (S)
- Computation (C)

Edge Types:
- Read (R)
  - C --> ES
  - C --> EG
  - C --> S
- Write (W)
  - C --> S
  - C --> ES
- Trigger (T)
  - C --> C
  - S --> C

## Responsibilities

### Internal State Variable
- Can the cached return value of a function or an independent variable written any computation.
- Multiple computations are allowed to write the same state variable.

### Computation
- Has at least one state variable it writes to: the return value.
- If it writes to an independent variable then this write needs to be removed if it doesn't write
  to it anymore after re-validation.

## Use Case "Typesystem"

![](dependency-graph.png)

- A state variable is written by a computation (from the typesystem)
  that is unknown to the reader (a computation from the editor).
- Before reading the variable the whole path from the typesystem root to the written variable
  has to be validated. 

## Use Case "Functional"

![](dependency-graph-functional.png)

- There are no writes to state variables.
  Computations just mutate the cache entry containing their return value.
- The variable (cache entry) knows statically which computation writes to it.
- A computation doesn't trigger/call other computations.
  It just reads a variable and the variable is responsible for validating itself.