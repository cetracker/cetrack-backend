# Part PartType Relations

# Different Parts, one PartType

| Date                | PartType | Part A | Part B | Part C | Action                                             |
|---------------------|----------|:------:|:------:|:------:|----------------------------------------------------|
| 2022-02-01 ->       | Crank    |   X    |        |        |                                                    |
|                     |          |        |        |        | _Replace Crank A with B_                           |
| 2022-02-01 -> 03-01 | Crank    |  ->/   |        |        | Terminate most recent relation for part type       |
| 2022-03-02 -> 03-01 | Crank    |        |   X    |        | Add new open ended relation for B                  |
|                     |          |        |        |        | _Replace Crank B with C_                           |
| 2022-02-01 -> 03-01 | Crank    |  <->   |        |        | Relation for A is left untouched                   |
| 2022-03-01 -> 03-14 | Crank    |        |  ->/   |        | Terminate most recent relation                     |
| 2022-03-15 ->       | Crank    |        |        |   X    | Add new open ended relation for C                  |
|                     |          |        |        |        | _Try to add a new Relation for C valid from 04-15_ |
| 2022-03-15 ->       | Crank    |        |        |   X    | Last open ended relation is still valid            |
|                     |          |        |        |        | _Try to add a new Relation for B valid from 02-15_ |
|                     |          |        |        |        | ✗ **NO_NEW_RELATION_WITHIN_CLOSED_RANGE**          |

# Different Parts, different PartType

| Date                | PartType   | Part X | Part Y | Part Z | Action                                                                                     |
|---------------------|------------|:------:|:------:|:------:|--------------------------------------------------------------------------------------------|
| 2022-01-10 -> 01-31 | Rear Tire  |        |        |   X    |                                                                                            |
| 2022-02-10 ->       | Rear Tire  |        |   X    |        |                                                                                            |
| 2022-02-01 ->       | Front Tire |   X    |        |        |                                                                                            |
|                     |            |        |        |        | _Use X as Rear Tire_                                                                       |
| 2022-01-10 -> 01-31 | Rear Tire  |        |        |  <->   | Relation Z as RT is left untouched                                                         |
| 2022-02-01 -> 02-28 | Front Tire |  ->/   |        |        | Terminate most recent open ended relation for X since it's now used as different part type |
| 2022-02-10 -> 02-28 | Rear Tire  |        |  ->/   |        | Terminate most recent open ended relation for part type                                    |
| 2022-03-01 ->       | Rear Tire  |   X    |        |        | Add new open ended relation for X as Rear Tire                                             |
|                     |            |        |        |        | _Try to use X as Front Tire from 02-15_                                                    |
| 2022-02-01 -> 02-28 | Rear Tire  |  <->   |        |        | Relation X as FT is left untouched                                                         |
| 2022-03-01 ->       | Rear Tire  |   !!   |        |        | ✗ **NO_NEW_RELATION_WITHIN_CLOSED_RANGE**                                                  |
