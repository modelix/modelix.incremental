# Incremental
Engine for incremental computation

## Features
- **Dynamic Dependency Analysis**:
  The dependency graph is built from the actual access to input model elements during the execution of a function.
  A function is re-executed if one the inputs changes.
  To incrementalize a computation only a decomposition into small recursive functions is required.
  The result of the function calls is cached and invalidated by the engine.
- **Scalability**:
  If the input data can be structured hierarchically then the engine is able to automatically adjust the granularity
  of the dependency tracking.
  The dependency graph is more detailed for parts that change often and less detailed for those that change less
  frequently.
  The graph size required for a good performance is proportional to the working set.
  The size of the input model is mostly irrelevant.
- **Push and Pull**:
  [TODO]
  Computations are not required to be functional.
  They are also allowed to write to state variables and this write access is recorded in the dependency graph.
  This means that the data flow cannot just be implemented by the output 'pulling' from the input,
  but also by the input 'pushing' towards the output.
