# Scotland Yard AI agent

AI agent designed to play the Mr. X role in Scotland Yard, using game state information to flee from the detectives.

## Overview
This project was developed as a part of a university coursework to explore artificial intelligence in game environment. The first part consisted of implementing different design patterns into a A GUI skeleton that was given using Java. Most notably Factory, Visitor, 
and Observer patterns, with the aim of understanding the general use of them in other programming languages as well. [See here](./cw-model).  

The second part implementation is in this repository. The goal was to design an AI system by choosing suitable algorithms that will enable 
Mr. X to evaluate positions of detectives, their possible moves, his available moves, and decide on the best moves forward. [See here](./cw-ai/src/main/java/uk/ac/bris/cs/scotlandyard/ui/ai/Filipek.java).

## Game mechanics
In Scotland Yard, one of the players takes on the role of Mr. X. His job is to move from point to point around the map of London taking taxis, buses or subways. The detectives – that is, the remaining players acting in concert – move around similarly 
in an effort to move into the same space as Mr. X. But while the criminal's mode of transportation is nearly always known, his exact location is only known intermittently throughout the game. ([Scotland Yard - Board Game Geek](https://boardgamegeek.com/boardgame/438/scotland-yard))

## Objectives
- Implement a functioning AI agent
- Decide what game states are going to affect the optimal actions
- Compare different search and decision-making approaches
- Evaluate performance

## Implementation
A minimax algorithm was implemented to explore the game tree and search multiple moves in advance to determine the best move.
A scoring function is used to score intermediate positions where the game is not yet over. 
Its scores the current state of game that minimax explores and is derived from the distance between Mr. X and the detectives. 
The shortest possible distance from the Mr. X to a detective is computed by Breadth-First-Search (BFS) algorithm. It takes
into account the tickets that the detective possesses, such that only possible moves are counted. 
To optimise the minimax algorithm, alpha-beta pruning was used, which stops evaluating a move once it is found to lead 
to a worse outcome than a previously evaluated move, significantly cutting the execution runtime. 

## Technical details
Built with Java.
Runs with Maven.
Core concepts: Minimax, Breadth-First-Search, Alpha-Beta pruning

## Results

## How to run
clone the repository and move into the folder
```
git clone https://github.com/filipek-0/ScotlandYard-AI/
cd ScotlandYard-AI
```
to run the tests of first part of the coursework
```
mvn clean test
```
to play the game (opens the GUI)
```
mvn clean compile exec:java
```
Choose the setup and AI agent for Mr. X:

![GUI Explanation](./documentation/GUI_explanation.png)

## Acknowlegements
University of Bristol — Object Oriented Programming coursework
Original *Scotland Yard* board game by Ravensburger
