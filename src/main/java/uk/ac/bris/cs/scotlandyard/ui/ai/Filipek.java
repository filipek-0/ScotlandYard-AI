package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.*;

import static uk.ac.bris.cs.scotlandyard.model.Piece.Detective;
import static uk.ac.bris.cs.scotlandyard.model.Piece.MrX;

public class Filipek implements Ai {

	@Nonnull @Override
	public String name() { return "Filipek"; }

	@Nonnull @Override
	public Move pickMove(
			@Nonnull Board board,
			Pair<Long, TimeUnit> timeoutPair) {

		// get all available moves and store them in moves variable
		var moves = board.getAvailableMoves().asList();
		// map to store scores of every move
		Map<Move, Integer> scoreForMoves = new HashMap<>();

		// calls score for all the available moves
		for (var move : moves) {
			if (move instanceof Move.SingleMove) {
				scoreForMoves.put(move, score(board, (Move.SingleMove) move));
				System.out.println("for " + ((Move.SingleMove) move).ticket + " to " + ((Move.SingleMove) move).destination + " the score: " + score(board, (Move.SingleMove) move));
			}
			else if (move instanceof Move.DoubleMove) {
				scoreForMoves.put(move, score(board, (Move.DoubleMove) move));
				System.out.println("for " + ((Move.DoubleMove) move).ticket1 + " " + ((Move.DoubleMove) move).ticket2 + " to " + ((Move.DoubleMove) move).destination2 + " the score: " + score(board, (Move.DoubleMove) move) + " DOUBLE");

			}
		}
		// pick the best move with the highest score, and return the key for it, so the move itself
		Move bestMove = scoreForMoves.entrySet().stream()
				.max(Map.Entry.comparingByValue())
				.map(Map.Entry::getKey)
				.orElse(null);

		return bestMove;
	}


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
	private int scoreConnectivity(Board board, int source, int destination, int weight) {
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
				score += weight; // Stream terminates immediately once condition met
			}
		}

		return score;
	}

	// Returns all detectives as players (Used in: score())
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
