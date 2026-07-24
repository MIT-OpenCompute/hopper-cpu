# Pipeline

Each module in the pipeline needs to support two additional operations:
1. Flush
2. Stall

## Flush
A flush signal indicates that the current outputted value must be flushed and marked as invalid.

## Stall
Similarly a stall signal indicates the the output latched registers must not be updated. This may also require a module to propagate stall backwards up the pipeline. However, if a stage may take multiple inputs at a time, it need only stall once full and otherwise must simply track its state to not corrupt on a stall.