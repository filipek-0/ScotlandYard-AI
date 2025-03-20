package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.atlassian.fugue.Pair;
import net.bytebuddy.matcher.MethodOverrideMatcher;
import uk.ac.bris.cs.scotlandyard.model.*;

import static uk.ac.bris.cs.scotlandyard.model.Piece.Detective;
import static uk.ac.bris.cs.scotlandyard.model.Piece.MrX;

public class Filipek implements Ai {

	@Nonnull @Override
	public String name() { return "Filipek"; }

	@Nonnull @Override
	public Move pickMove(@Nonnull Board board, Pair<Long, TimeUnit> timeoutPair) {
		// No. of turns to look ahead (turn = Mr. X move + all detective moves)
		final int TURNS = 1;

		Board.GameState gameState = createGameState(board);


		ImmutableSet<Move> moves = gameState.getAvailableMoves();

		int bestEval = Integer.MIN_VALUE;
		Move bestMove = moves.asList().get(0);
		// So that we never return a null move, initialise bestMove to the first move

		for (Move move : moves) {
			// Note: Depth is multiplied
			int eval = minimax(gameState.advance(move), TURNS * 2, false);
			if (eval > bestEval) {
				bestEval = eval;
				bestMove = move;
			}
		}

		return bestMove;
	}

	// Note: MaxPlayer = Mr. X
	public int minimax(Board.GameState gameState, int depth, boolean isMaxPlayer) {
		System.out.println("Current depth: " + depth);
		// First, check for a winner
		if (!gameState.getWinner().isEmpty()) {
			return (gameState.getWinner().equals(ImmutableSet.of(MrX.MRX))) ? Integer.MAX_VALUE : Integer.MIN_VALUE;
		}
		// If depth = 0, statically evaluate a board
		if (depth == 0) return minimaxScore(gameState);

		// If we don't yet have a winner, we can assume getAvailableMoves() is NOT empty
		ImmutableSet<Move> moves = gameState.getAvailableMoves();

		// If evaluating Mr. X's moves:
		if (isMaxPlayer) {
			int maxEval = Integer.MIN_VALUE;
			for (Move move : moves) {
				int eval = minimax(gameState.advance(move), depth - 1, false);
				maxEval = Math.max(maxEval, eval);
			}

			return maxEval;
		}
		// Else, if evaluating the detective's moves:
		else {
			// TODO ERROR: DOESN'T WORK FOR MULTIPLE DETECTIVES!
			int minEval = Integer.MAX_VALUE;
			for (Move move : moves) {
				int eval = minimax(gameState.advance(move), depth - 1, true);
				minEval = Math.min(minEval, eval);
			}

			return minEval;
		}
	}

	// TODO: ONLY WORKS WHEN CALLED ON MR Xs TURN, MAY OR MAY NOT BE A PROBLEM IM NOT SURE
	private int minimaxScore(Board board) {
		final int DIST_WEIGHT = 10;

		// Get the ITERATOR over getAvailableMoves()
		Iterator<Move> moveIterator = board.getAvailableMoves().iterator();
		// To get the current location, use the iterator to get the first move, and call source() ;)
		int source = moveIterator.next().source();
		// NOTE: minmaxScore() is never called from a position where getAvailableMoves() would be empty


		// Score a move based off how far Mr. X is from the two closest detectives
		List<Integer> distances = detectiveDistances(board, source);

		int min1 = Integer.MAX_VALUE;
		int min2 = Integer.MAX_VALUE;

		for (int distance : distances) {
			if (distance < min1) {
				min2 = min1;
				min1 = distance;
			}
			else if (distance < min2) min2 = distance;
		}

		// If there is only one detective (i.e. two players), don't factor in the second min. distance
		if (board.getPlayers().size() == 2) return (DIST_WEIGHT * (min1 - 3));

		return (int) (DIST_WEIGHT * ((min1 - 3) + Math.floor(0.5 * (min2 - 2))));
	}

	private Board.GameState createGameState(Board board) {
		// NOTE: Works on Mr. X's turn ONLY
		// Get the ITERATOR over getAvailableMoves()
		Iterator<Move> moveIterator = board.getAvailableMoves().iterator();
		// Use the iterator to get the first Mr. X move, then call source() for his location
		int mrXLocation = moveIterator.next().source();
		Player mrX = new Player(MrX.MRX, convertTickets(board.getPlayerTickets(MrX.MRX).get()), mrXLocation);

		ImmutableList.Builder<Player> builder = ImmutableList.builder();
		for (Piece piece : board.getPlayers()) {
			if (piece.isDetective()) {
				builder.add(new Player(piece,
						convertTickets(board.getPlayerTickets(piece).get()),
						board.getDetectiveLocation((Detective) piece).get())
				);
			}
		}

		return new MyGameStateFactory().build(board.getSetup(), mrX, builder.build());
	}



	// TODO make global constants for the scoring
	private int score(@Nonnull Board board, Move.SingleMove move) {
		// The final score of the move
		int score = 0;

		// Adds score for each node adjacent to the destination
		final int CONNECTIVITY = 5;
		// weights the distances from the detectives
		final int WEIGHT_DISTANCE = 40;
		// (Secret Move) Modify score based on different transport types at source
		final int[] SECRET_CONNECTIVITY = {-10, 0, 15};

		// Scores the connectivity of the destination node, increasing
		// the score for each adjacent available move at the destination
		score += scoreConnectivity(board, move.source(), move.destination, CONNECTIVITY);

		// Modify score based on the distances to the detectives where the moves ends up compared to the location where mrX starts
		score += scoreDistances(board, move.source(), move.destination, WEIGHT_DISTANCE);

		// Evaluate the use of secret tickets based off of the NUMBER OF
		// DIFFERENT TYPES of modes of transport available at the SOURCE
		if (move.ticket == ScotlandYard.Ticket.SECRET)
			score += scoreSecret(board, move.source(), SECRET_CONNECTIVITY);

		return score;
	}

	// scores double moves
	private int score(@Nonnull Board board, Move.DoubleMove move) {
		// Slightly discouraged from using a double ticket
		int score = -10;

		// Adds score for each node adjacent to the destination
		final int CONNECTIVITY = 5;
		// weights the distances from the detectives
		final int WEIGHT_DISTANCE = 40;
		// (Secret Move) Modify score based on different transport types at source
		final int[] SECRET_CONNECTIVITY = {-10, 5, 15};

		score += scoreConnectivity(board, move.source(), move.destination2, CONNECTIVITY);

		score += scoreDistances(board, move.source(), move.destination2, WEIGHT_DISTANCE);

		if (move.ticket1 == ScotlandYard.Ticket.SECRET)
			score += scoreSecret(board, move.source(), SECRET_CONNECTIVITY);

		if (move.ticket2 == ScotlandYard.Ticket.SECRET)
			score += scoreSecret(board, move.source(), SECRET_CONNECTIVITY);

		return score;
	}



	// ---------- Helper Functions ----------
	private int scoreDistances(Board board, int source, int destination, int WEIGHT_DISTANCE) {
		// distances at the starting location of the move, the source
		List<Integer> detectiveDistancesSource = detectiveDistances(board, source);
		// distances at the destination of the move
		List<Integer> detectiveDistancesDestination = detectiveDistances(board, destination);

		double initialDanger = detectiveDistancesSource.stream()
				.mapToDouble(distance -> 1.0 / distance)
				.sum();
		double nextDanger = detectiveDistancesDestination.stream()
				.mapToDouble(distance -> 1.0 / distance)
				.sum();
		double dangerChange = initialDanger - nextDanger;

		return (int) Math.floor(WEIGHT_DISTANCE * dangerChange);
	}

	// Scores the connectivity of the destination node (Used in: score())
	private int scoreConnectivity(Board board, int source, int destination, int WEIGHT) {
		// Increase score for each adjacent available move at the destination
		int score = 0;


		// mrX's tickets
		Optional<Board.TicketBoard> mrXTicketBoard = board.getPlayerTickets(MrX.MRX);
		ImmutableMap<ScotlandYard.Ticket, Integer> mrXTickets = ImmutableMap.of();
		if (mrXTicketBoard.isPresent())
			mrXTickets = convertTickets(mrXTicketBoard.get());
		else
			mrXTickets = ImmutableMap.of();


		for (int adjacentNode : board.getSetup().graph.adjacentNodes(destination)) {
			// First, check if the location is occupied
			if (checkOccupied(board, adjacentNode)) continue;

			// ...and check that Mr. X can actually make that adjacent available move
			// First, get all the possible tickets to use
			ImmutableSet<ScotlandYard.Transport> moves
					= board.getSetup().graph.edgeValueOrDefault(source, destination, ImmutableSet.of());

			ImmutableMap<ScotlandYard.Ticket, Integer> finalMrXTickets = mrXTickets;
			if (moves.stream().anyMatch(transport -> finalMrXTickets.getOrDefault(transport.requiredTicket(), 0) > 0)) {
				score += WEIGHT; // Stream terminates immediately once condition met
			}
		}

		return score;
	}

	// Returns the distance of each detective to Mr. X (Used in: score())
	private List<Integer> detectiveDistances(Board board, int mrXLocation) {
		List<Integer> distances = new ArrayList<>();
		for (Piece piece : board.getPlayers()) {
			if (piece.isDetective()) {
				Optional<Board.TicketBoard> tickets = (board.getPlayerTickets(piece));
				Optional<Integer> detectiveLocation = board.getDetectiveLocation((Detective) piece);
				if (tickets.isPresent() && detectiveLocation.isPresent()) {
//					System.out.println("current detective location: " + detectiveLocation.get() + " mrXLocation: " + mrXLocation);
					distances.add(distanceFromDetective(board, mrXLocation, detectiveLocation.get(), tickets.get()));
				}
			}
		}
		return distances;
	}

	// Evaluates the use of a secret ticket for a move (Used in: score())
	private int scoreSecret(Board board, int moveSource, int[] secretConnectivity) {
		// More possible transport types = better use of secret ticket

		HashSet<ScotlandYard.Transport> transports = new HashSet<>();

		// For each possible move, adds the different types of transport to a hash set
		for (int adjacentNode : board.getSetup().graph.adjacentNodes(moveSource)) {
			transports.addAll(board.getSetup().graph.edgeValueOrDefault(moveSource, adjacentNode, ImmutableSet.of()));
			// There are only max 3 different transport types, save some time...
			if (transports.size() == 3) break;
		}

		return secretConnectivity[transports.size() - 1];
	}

	// Checks if a location is occupied by a detective (Used in: destinationConnectivity())
	private boolean checkOccupied(@Nonnull Board board, int location) {
		for (Piece piece : board.getPlayers()) {
			if (piece.isDetective()) {
				Optional<Integer> detectiveLocation = board.getDetectiveLocation((Piece.Detective) piece);
				if (detectiveLocation.isPresent() && detectiveLocation.get() == location) return true;
			}
		}
		return false;
	}

	// Get the shortest distance from a detective to Mr. X (Used in: detectiveDistances())
	// TODO Add a way of keeping track of the tickets, possibly by adding a ticket board to the pairs
	public int distanceFromDetective(@Nonnull Board board, int target, int source, Board.TicketBoard ticketBoard) {
		// tickets that detective possesses
		ImmutableMap<ScotlandYard.Ticket, Integer> tickets = convertTickets(ticketBoard);

		// Visited nodes
		HashSet<Integer> visited = new HashSet<>();
		// Distance of each node from source
		HashMap<Integer, Integer> distances = new HashMap<>();
		// Queue of nodes to be visited
		Queue<moveState> queue = new LinkedList<>();

		// Initialise all distances with infinity initially (no null values)
		board.getSetup().graph.nodes().forEach(location -> distances.put(location, Integer.MAX_VALUE));

		// set the source (detective location) to have distance 0 to itself
		distances.replace(source, 0);
		// set the moveState for the source node, where detective is standing
		moveState sourceState = new moveState(source, null, tickets);

		// Add the source's adjacent nodes to the queue, and mark it as visited
		for (int adjacentNode : board.getSetup().graph.adjacentNodes(source)) {
			for (ScotlandYard.Transport ticketType : board.getSetup().graph.edgeValueOrDefault(source, adjacentNode, ImmutableSet.of()))
			{
				// if detective has the ticket for the transport needed to the adjacent node, use it and add to queue a new move state with updated tickets
				if(tickets != null && tickets.containsKey(ticketType.requiredTicket()) && tickets.get(ticketType.requiredTicket()) > 0) {
					ImmutableMap<ScotlandYard.Ticket, Integer> newTickets = useTicket(tickets, ticketType);
					queue.add(new moveState(adjacentNode, sourceState, newTickets));
				}
			}
		}
		visited.add(source);

		while (!queue.isEmpty()) {
			// Get next node off of queue
			moveState current = queue.poll();
			// Update the distance to the current node
			int minDistance = Math.min(distances.get(current.getPosition()), distances.get(current.getParent().getPosition()) + 1);
			distances.replace(current.getPosition(), minDistance);

			// If we've reached our target, return the distance to it
			if (current.getPosition() == target) {
//				System.out.println(current.getTickets().toString());
				return distances.get(current.getPosition());
			}

			// If the node has not yet been visited, add adjacent nodes to the queue and mark as visited
			if (!visited.contains(current.getPosition())) {
//				System.out.println("not yet visited: " + current.getPosition());
				for (int adjacentNode : board.getSetup().graph.adjacentNodes(current.getPosition())) {
//					System.out.println(adjacentNode);
					for (ScotlandYard.Transport ticketType : board.getSetup().graph.edgeValueOrDefault(current.getPosition(), adjacentNode, ImmutableSet.of()))
					{
						ImmutableMap<ScotlandYard.Ticket, Integer> ticketsHere = current.getTickets();	// current MoveState has already only the correct tickets left
//						System.out.println(ticketType.requiredTicket());
						int ticketsOfType = (ticketsHere != null && tickets.get(ticketType.requiredTicket()) != null) ? ticketsHere.get(ticketType.requiredTicket()) : 0;
//						System.out.println(ticketsOfType);
						if(ticketsOfType > 0) {
							ImmutableMap<ScotlandYard.Ticket, Integer> newTickets = useTicket(ticketsHere, ticketType);
							queue.add(new moveState(adjacentNode, current, newTickets));
						}
					}
				}
				visited.add(current.getPosition());
			}
		}

		//System.out.println(distances);

		// Otherwise, target not found i.e. not reachable - return max distance
		return Integer.MAX_VALUE;
	}

	// Return new ImmutableMap of TicketBoard that uses the ticket for the transport (Used in: distanceFromDetective())
	public ImmutableMap<ScotlandYard.Ticket, Integer> useTicket(ImmutableMap<ScotlandYard.Ticket, Integer> tickets,
																ScotlandYard.Transport transport) {
		ImmutableMap.Builder<ScotlandYard.Ticket, Integer> builder = ImmutableMap.builder();

		for (ScotlandYard.Ticket ticket : tickets.keySet()) {
			if (transport.requiredTicket().equals(ticket)) {
				builder.put(ticket, tickets.get(ticket) - 1);
			}
			else {
				builder.put(ticket, tickets.get(ticket));
			}
		}
		return builder.build();
	}

	// Convert from TicketBoard into Map<Ticket, Integer> (Used in: distanceFromDetective())
	public ImmutableMap<ScotlandYard.Ticket, Integer> convertTickets(Board.TicketBoard ticketBoard) {
		// map to store the tickets
		Map<ScotlandYard.Ticket, Integer> tickets = new HashMap<>();

		// iterate over all ticket types and add according counts to the map
		for (ScotlandYard.Ticket ticket : ScotlandYard.Ticket.values()) {
			tickets.put(ticket, ticketBoard.getCount(ticket));
		}
		return ImmutableMap.copyOf(tickets);
	}
	// --------------------

	// ---------- Helper Classes ----------
	// Class to hold the nodes that distanceFromDetective searches through, and tickets left
	class moveState {
		int position;
		moveState parent;
		ImmutableMap<ScotlandYard.Ticket, Integer> tickets;

		moveState(int position, moveState parent, ImmutableMap<ScotlandYard.Ticket, Integer> tickets) {
			this.position = position;
			this.parent = parent;
			this.tickets = tickets;
		}

		public int getPosition() {
			return position;
		}

		public moveState getParent() {
			return parent;
		}

		public ImmutableMap<ScotlandYard.Ticket, Integer> getTickets() {
			return tickets;
		}
	}
	// --------------------

}
