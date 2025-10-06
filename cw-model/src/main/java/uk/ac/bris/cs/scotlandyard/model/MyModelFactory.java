package uk.ac.bris.cs.scotlandyard.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import javax.annotation.Nonnull;

import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Factory;

import java.util.HashSet;
import java.util.Set;

/**
 * cw-model
 * Stage 2: Complete this class
 */

public final class MyModelFactory implements Factory<Model> {

	@Nonnull @Override public Model build(GameSetup setup,
										  Player mrX,
										  ImmutableList<Player> detectives) {

		// create an instance of MyGameStateFactory which then builds the initial GameState
		MyGameStateFactory myGameStateFactory = new MyGameStateFactory();
		Board.GameState gameState = myGameStateFactory.build(setup, mrX, detectives);
		return new myModel(gameState);
	}

	private final class myModel implements Model {
		private Board.GameState gameState;
		private Set<Observer> observers;

		public myModel(Board.GameState gameState) {
			this.gameState = gameState;
			observers = new HashSet<Observer>();
		}

		@Override @Nonnull public Board getCurrentBoard() {
			return gameState;
		}

		@Override public void chooseMove(@Nonnull Move move){
			//  Advance the model with move, then notify all observers of what just happened.
			gameState = gameState.advance(move);

			if (gameState.getWinner().isEmpty()) {
				for (Observer observer : observers) {
					observer.onModelChanged(gameState, Observer.Event.MOVE_MADE);
				}
			}
			else {
				for (Observer observer : observers) {
					observer.onModelChanged(gameState, Observer.Event.GAME_OVER);
				}
			}
		}

		@Override public void registerObserver(@Nonnull Observer observer) {
			if (observer == null) throw new NullPointerException();

			// if observer is already registered, add() returns false, return exception
			if (!observers.add(observer)) {
				throw new IllegalArgumentException("Observer already registered: " + observer);
			}
		}

		@Override public void unregisterObserver(@Nonnull Observer observer) {
			if (observer == null) throw new NullPointerException();

			// if observer is not an element of the set, return exception
			if (!observers.remove(observer)) {
				throw new IllegalArgumentException("Observer never registered: " + observer);
			}
		}

		@Override @Nonnull public ImmutableSet<Observer> getObservers() {
			return new ImmutableSet.Builder<Observer>().addAll(observers).build();
		}
	}
}