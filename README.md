# Trade-offs
## Money precision, Money arithmetic 
DECIMAL (100, 10), casts to strings, string money in JSON

## Event sourcing and why it's not here
While event sourcing is usually a great fit for money-processing systems, it requires a lot infrastructure to
setup correctly:
- Event processing pipeline
- Test setup that handles eventual consistency
- Edge-cases of eventual consistency (e.g. concurrency issues with money withdrawal/transfers)
    
This is a small example project, so it uses audit log + transactions instead of an event-processing system which 
would be a overkill.

## ORM
No ORM 

## Logging
JUL