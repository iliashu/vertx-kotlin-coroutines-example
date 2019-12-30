# Vert.x + Kotlin Coroutines + JDBC Example
This repo contains a simple working example of Kotlin Vert.x project,
along with simple database, tests, api specification, and a dockerfile

# Money precision and arithmetic 
In this project, money are represented as strings in JSON, and BigDecimal in domain code.
The reason for this is that floating point arithmetic is generally a bad fit for any operations with money[^0][^1]

# ORM
This project uses no ORM, at least at the moment. 
I just could not find any light-weight ORM that works with Vert.x way of doing SQL/JDBC

# DI
This project is so small that adding DI would just make things a lot more complicated, 
less readable and way more heavy-weight

[^0]: https://husobee.github.io/money/float/2016/09/23/never-use-floats-for-currency.html
[^1]: https://dzone.com/articles/never-use-float-and-double-for-monetary-calculatio