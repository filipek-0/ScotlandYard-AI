package uk.ac.bris.cs.scotlandyard.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import javax.annotation.Nonnull;

import uk.ac.bris.cs.scotlandyard.model.Board.GameState;
import uk.ac.bris.cs.scotlandyard.model.Move.*;
import uk.ac.bris.cs.scotlandyard.model.Piece.*;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.*;

import java.util.*;

/**
 * cw-model
 * Stage 1: Complete this class
 */
public final class MyGameStateFactory implements Factory<GameState> {

	@Nonnull @Override
	public GameState build(GameSetup setup, Player mrX, ImmutableList<Player> detectives) {
		return new MyGameState(setup, mrX, detectives, ImmutableSet.of(MrX.MRX), ImmutableList.of());
	}

	private final class MyGameState implements GameState {
		private final GameSetup setup;						// The game setup - lets us access the graph + Mr X's reveal moves
		private final Player mrX;							// Holds Mr X
		private final List<Player> detectives;				// Holds the detectives
		private final ImmutableList<LogEntry> log;			// Log of Mr X's moves and the ticket used (bus, taxi etc.)
		private final ImmutableSet<Piece> remaining;		// Pieces that can still move this round
		private final ImmutableSet<Move> moves;				// Holds the currently possible/available moves
		private final ImmutableSet<Piece> winners;			// Holds the winner/s


		private MyGameState(
				final GameSetup setup,
				final Player mrX,
				final List<Player> detectives,
				final ImmutableSet<Piece> remaining,
				final ImmutableList<LogEntry> log) throws IllegalArgumentException, NullPointerException {

			// First, initialize all values
			this.setup = setup;
			this.mrX = mrX;
			this.detectives = detectives;
			this.remaining = remaining;
			this.log = log;
			// winner MUST be initialized before moves
			winners = getWinner();
			moves = getAvailableMoves();

			// Then, check them all for validity, in line with the tests
			testInitialisation(this.setup, this.mrX, this.detectives);
		}


		// -------- Separate method for testing variables (to keep the constructor cleaner) --------
		private void testInitialisation(final GameSetup setup,
								   final Player mrX,
								   final List<Player> detectives) throws IllegalArgumentException, NullPointerException {

			// setup
			if(setup.moves.isEmpty()) throw new IllegalArgumentException("Moves is empty!");

			// mrX
			if (mrX == null) throw new NullPointerException("Mr X is null!");
			if (mrX.isDetective()) throw new IllegalArgumentException("No Mr X!");

			// detectives
			// check if there are any detectives present
			if (detectives == null) throw new NullPointerException("Detectives set is null!");

			// set that will be containing all the detectives, set CANNOT contain duplicates, which we will use to throw exception
			Set<Player> detectivesSet = new HashSet<>();
			Set<Integer> locationsSet = new HashSet<>();

			// iterate over all detectives from the list
			for (Player detective : detectives) {
				if (detective == null) throw new NullPointerException("A detective is null!");
				if (detective.isMrX()) throw new IllegalArgumentException("A detective is Mr X!");
				if (detective.has(Ticket.DOUBLE)) throw new IllegalArgumentException("A detective has a double ticket!");
				if (detective.has(Ticket.SECRET)) throw new IllegalArgumentException("A detective has a secret ticket!");

				// Set.add() returns false, if the element is already present
				if (!detectivesSet.add(detective)) throw new IllegalArgumentException("Detectives are duplicate!");
				if (!locationsSet.add(detective.location())) throw new IllegalArgumentException("Locations are duplicate!");
				// graph setup
				if (setup.graph.nodes().isEmpty()) throw new IllegalArgumentException("Graph is empty!");
			}
		}
		// ------------------------------


		// --------------- Accessors/Getters ---------------
		@Override @Nonnull
		public GameSetup getSetup() {
			return setup;
		}

		@Override @Nonnull
		public ImmutableSet<Piece> getPlayers() {
			// build a new ImmutableSet using a builder
			ImmutableSet.Builder<Piece> builder = ImmutableSet.builder();
			// add mrX into the ImmutableSet
			builder.add(mrX.piece());
			// iterate over detectives to add to the ImmutableSet using forEach()
			detectives.forEach(detective -> builder.add(detective.piece()));

			// create a new ImmutableSet and return it straight away as the players set
			return builder.build();
		}

		@Override @Nonnull
		public Optional<Integer> getDetectiveLocation(Detective detective) {
			// If Detective.piece == detective, return location in an Optional.of();
			for (Player player : detectives) {
				if (player.piece().equals(detective)) return Optional.of(player.location());
			}
			// otherwise, return Optional.empty();
			return Optional.empty();
		}

		@Override @Nonnull
		public Optional<TicketBoard> getPlayerTickets(Piece piece) {
			// detectives
			for (Player detective : detectives) {
				if (detective.piece().equals(piece)) {
					// for the matching player, implement TicketBoard using a lambda expression
					// NOTE: This works because TicketBoard has a SINGLE unimplemented method (known as a functional interface!)
					return Optional.of(ticket -> {
                        // get count of the ticket type from ImmutableMap<Ticket, Integer>> tickets, passing ticket as key
                        return detective.tickets().getOrDefault(ticket, 0);
                    });
				}
			}

			// mrX
			if (mrX.piece().equals(piece)) {
				return Optional.of(ticket -> mrX.tickets().getOrDefault(ticket, 0));
			}

			// if no match is made, return empty
			return Optional.empty();
		}

		@Override @Nonnull
		public ImmutableList<LogEntry> getMrXTravelLog() {
			return log;
		}

		@Override @Nonnull
		public ImmutableSet<Piece> getWinner() {
			// If the winner has already been calculated, avoid recalculating
			if (winners != null && !winners.isEmpty()) return winners;

			// After every move, check whether a detective has captured Mr. X
			for (Player detective : detectives) {
				if (detective.location() == mrX.location()) return getDetectivesSet();
			}

			// Otherwise, once all detectives have moved (i.e. it's Mr. X's turn), check:
			if (remaining.equals(ImmutableSet.of(mrX.piece()))) {
				// If Mr. X can't move
				if (getAvailableMoves().isEmpty()) return getDetectivesSet();

				// If the log is full (Mr. X has escaped!)
				if (log.size() == setup.moves.size()) return ImmutableSet.of(mrX.piece());

				// Determine if NONE of the detectives can move, using the makeSingleMoves() method to get the
				// detective moves (this allows 'remaining' to be kept final)
				boolean noneCanMove = detectives.stream()
						.map(detective -> makeSingleMoves(setup, detectives, detective, detective.location()))
						.map(Set::isEmpty) 		// (Is each returned set of moves empty?)
						.reduce(true, (acc, bool) -> acc && bool);
				if (noneCanMove) return ImmutableSet.of(mrX.piece());
			}

			// Otherwise, it's the middle of the round - can't determine winner yet!
			return ImmutableSet.of();
        }

		@Override @Nonnull
		public ImmutableSet<Move> getAvailableMoves() {
			if (winners != null && !winners.isEmpty()) return ImmutableSet.of();

			ImmutableSet.Builder<Move> builder = ImmutableSet.builder();

			// If it is Mr. X's turn:
			if (remaining.contains(mrX.piece())) {
				// Get single moves
				Set<SingleMove> singleMoves = makeSingleMoves(setup, detectives, mrX, mrX.location());
				builder.addAll(singleMoves);
				// and if possible, get double moves too
				if (mrX.has(Ticket.DOUBLE) && setup.moves.size() >= 2) {
					Set<DoubleMove> doubleMoves = makeDoubleMoves(setup, detectives, mrX, mrX.location());
					builder.addAll(doubleMoves);
				}
			}
			// else, it is the detectives' turns:
			else {
				// For each detective who hasn't yet moved:
				for (Piece piece : remaining) {
					Player player = getPlayer(piece);
					if (player != null) {
						// Get that detective's single moves
						Set<SingleMove> singleMoves = makeSingleMoves(setup, detectives, player, player.location());
						builder.addAll(singleMoves);
					}
				}
			}

			return builder.build();
		}

		@Override @Nonnull
		public GameState advance(Move move) {
			// Check first that the move being made is legal
			if(!moves.contains(move)) throw new IllegalArgumentException("Illegal move being made: " + move);

			// Implements an (anonymous) inner class of the 'Visitor<T>' interface, which is
			// passed as an argument to the accept(Visitor<T> visitor) method of the 'move' class
			// NOTE: Could have been replaced with a lambda if the 'Visitor<T>' interface only had one
			// method to be implemented, but it has two... :(
			return move.accept(
					new Visitor<GameState>() {

						@Override
						public GameState visit(SingleMove move) {
							// Holds the updated set of remaining players
							ImmutableSet<Piece> updatedRemaining = updateRemaining(move.commencedBy());
							// Holds the updated Mr. X
							Player updatedMrX;

							// If mr X just moved:
							if (move.commencedBy().equals(mrX.piece())) {
								// Update Mr. X
								updatedMrX = mrX.use(move.ticket).at(move.destination);

								// initialise an ImmutableList builder that takes all elements of the travel log
								ImmutableList.Builder<LogEntry> newLog = new ImmutableList.Builder<>();
								newLog.addAll(getMrXTravelLog());
								// add the new log entry, checking if it's Mr X's reveal move
								if(setup.moves.get(log.size())) 	newLog.add(LogEntry.reveal(move.ticket, move.destination));
								else 								newLog.add(LogEntry.hidden(move.ticket));

								// Return new game state
								return new MyGameState(setup, updatedMrX, detectives, updatedRemaining, newLog.build());
							}

							// else, one of the detectives has moved:
							else {
								List<Player> newDetectives = new ArrayList<>(detectives);

								for (int i = 0; i < newDetectives.size(); i++) {
									// if this detective commenced the move, update the location and tickets
									if (newDetectives.get(i).piece().equals(move.commencedBy())) {
										newDetectives.set(i, newDetectives.get(i).at(move.destination).use(move.ticket));
										break; // breaks from the loop once detective found
									}
								}

								updatedMrX = mrX.give(move.ticket);

								return new MyGameState(setup, updatedMrX, newDetectives, updatedRemaining, log);
							}

						}

						@Override
						public GameState visit(DoubleMove move) {
							// NOTE: Since only Mr. X can make double moves, we know it was Mr. X's turn

							// Holds the updated set of remaining players
							ImmutableSet<Piece> updatedRemaining = updateRemaining(move.commencedBy());
							// Holds the updated Mr. X
							Player updatedMrX = mrX;

							// Update the log
							ImmutableList.Builder<LogEntry> newLog = new ImmutableList.Builder<>();
							newLog.addAll(log);
								// First move
							if(setup.moves.get(log.size())) 	newLog.add(LogEntry.reveal(move.ticket1, move.destination1));
							else 								newLog.add(LogEntry.hidden(move.ticket1));
							updatedMrX = updatedMrX.at(move.destination1).use(move.ticket1);

								// Second move
							if(setup.moves.get(log.size() + 1)) 	newLog.add(LogEntry.reveal(move.ticket2, move.destination2));
							else 									newLog.add(LogEntry.hidden(move.ticket2));
							updatedMrX = updatedMrX.at(move.destination2).use(move.ticket2).use(Ticket.DOUBLE);

							return new MyGameState(setup, updatedMrX, detectives, updatedRemaining, newLog.build());
						}
			});
		}
		// ------------------------------


		// --------------- Helper Methods ---------------

		// Gets all VALID single moves (used in: makeDoubleMoves(), getAvailableMoves(), getWinner())
		private static Set<SingleMove> makeSingleMoves(GameSetup setup, List<Player> detectives, Player player, int source) {
			Set<SingleMove> singleMoves = new HashSet<>();

			// For each adjacent location (adjacent = connected by a mode of transport):
			for (int destination : setup.graph.adjacentNodes(source)) {
				// First, check if that location is occupied
				boolean occupied = false;
				for (Player detective : detectives) {
					if (detective.location() == destination) {
						occupied = true;
						break;
					}
				}
				if (occupied) continue;

				// Next check each possible way of reaching the location, and if the player has the right ticket
				for (Transport t : setup.graph.edgeValueOrDefault(source, destination, ImmutableSet.of())) {
					if (player.has(t.requiredTicket())) {
						singleMoves.add(new SingleMove(player.piece(), source, t.requiredTicket(), destination));
					}
				}

				// If the player (mrX) has a secret ticket, also add that possible move to the set
				if (player.isMrX() && player.has(Ticket.SECRET)) {
					singleMoves.add(new SingleMove(player.piece(), source, Ticket.SECRET, destination));
				}
			}

			return singleMoves;
		}

		// Gets all VALID double moves (used in: getAvailableMoves())
		private static Set<DoubleMove> makeDoubleMoves(GameSetup setup,  List<Player> detectives, Player player, int source) {
			// Set to hold our new double moves
			Set<DoubleMove> doubleMoves = new HashSet<>();
			// Set for the first of the two moves to be made
			Set<SingleMove> firstMoves = makeSingleMoves(setup, detectives, player, source);

			for (SingleMove firstMove : firstMoves) {
				// Generate the second of the two moves by calling makeSingleMoves() again, using a player with
				// one less ticket of the type used for the first move, and the destination as the source
				// NOTE: This works (original player object is not changed) because Java is pass-by-value!!!!!
				Set<SingleMove> secondMoves =
						makeSingleMoves(setup, detectives, player.use(firstMove.ticket), firstMove.destination);

				// Since makeSingleMoves() only generates valid moves, we can just add them to our doubleMoves
				// set without further checks
				for (SingleMove secondMove : secondMoves) {
					doubleMoves.add(
							new DoubleMove(player.piece(), firstMove.source(),
										  firstMove.ticket, firstMove.destination,
										  secondMove.ticket, secondMove.destination)
					);
				}

			}

			return doubleMoves;
		}

		// Return the Player associated with Piece (used in: getAvailableMoves())
		Player getPlayer(Piece piece) {
			if (mrX.piece().equals(piece)) return mrX;
			for (Player detective : detectives) {
				if (detective.piece().equals(piece)) return detective;
			}
			return null;
		}

		// Update the set of remaining Players (used in: advance())
		public ImmutableSet<Piece> updateRemaining(Piece justPlayed) {
			// Holds the set of players yet to play
			ImmutableSet.Builder<Piece> newRemaining = ImmutableSet.builder();

			// if mrX just played, detectives WHO CAN MOVE are added to remaining
			if (justPlayed.equals(mrX.piece())) {
				detectives.forEach(detective -> {
					if (!makeSingleMoves(setup, detectives, detective, detective.location()).isEmpty())
						newRemaining.add(detective.piece());
				});
			}
			// else if detective just played, add all other detectives into updatedRemaining except this one
			else {
				for (Piece piece : remaining) {
					if (!piece.equals(justPlayed)) {
						newRemaining.add(piece);
					}
				}
			}

			ImmutableSet<Piece> builtSet = newRemaining.build();

			// if the updated (new) remaining is empty, no detectives are left, initialise new mrX's turn (next round)
			return builtSet.isEmpty() ? ImmutableSet.of(mrX.piece()) : builtSet;
		}

		// Return a set of detectives (used in: getWinner())
		public ImmutableSet<Piece> getDetectivesSet() {
			ImmutableSet.Builder<Piece> builder = ImmutableSet.builder();

			detectives.forEach(detective -> builder.add(detective.piece()));

			return builder.build();
		}
		// ------------------------------
	}

}
