- Maximum loan period is 60 months instead of 48 months specified in the task of TICKET-101. UI has a slider with period ranging from 12 to 60 months but the label below it shows a minimum value of 6.
- To get a credit modifier, the method `getCreditModifier` takes the last 4 digits of personal code and checks whether the number is over 2500, 5000 or 7500. This in fact has hard coded results for getting a credit score but the examples from the task of TICKET-101 can only be used for testing loans for people in debt.
- Inputs are verified as expected. Personal code goes through the validator, loan period and amount are both checked to be inside the range. Condition `!(min <= value) || !(value <= max)` can be replaced with `(min <= value) && (value <= max)` so that there are fewer operations.
- There is documentation for classes and methods.

**Problem:**
Current solution calculates the highest valid loan amount using some other method, but we need to use the credit score.