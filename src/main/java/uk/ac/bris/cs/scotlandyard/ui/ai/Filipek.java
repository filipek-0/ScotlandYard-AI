package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import impl.org.controlsfx.tools.rectangle.change.NewChangeStrategy;
import io.atlassian.fugue.Pair;
import org.controlsfx.control.tableview2.filter.filtereditor.SouthFilter;
import org.glassfish.grizzly.Transport;
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

		// TODO: next 5 lines just for a quick test, try and change the ticket counts and locations
//		ImmutableMap.Builder<ScotlandYard.Ticket, Integer> detectiveTickets = ImmutableMap.builder();
//		detectiveTickets.put(ScotlandYard.Ticket.TAXI, 2);
//		detectiveTickets.put(ScotlandYard.Ticket.BUS, 1);
//		detectiveTickets.put(ScotlandYard.Ticket.UNDERGROUND, 0);
//		System.out.println(distanceFromDetective(board, new Player(Detective.BLUE, detectiveTickets.build(), 179), new Player(MrX.MRX, ScotlandYard.defaultMrXTickets(), 168)));

		// mrX's tickets
		Optional<Board.TicketBoard> mrXTicketBoard = board.getPlayerTickets(MrX.MRX);
		ImmutableMap<ScotlandYard.Ticket, Integer> mrXTickets = ImmutableMap.of();
		if (mrXTicketBoard.isPresent())
			mrXTickets = convertTickets(mrXTicketBoard.get());

		var moves = board.getAvailableMoves().asList();
		// map to store scores of every move
		Map<Move, Integer> scoreForMoves = new HashMap<>();

		// calls score for all the available moves
		for(var move : moves) {
			if (move instanceof Move.SingleMove) {
				scoreForMoves.put(move, score(board, (Move.SingleMove) move, mrXTickets));
				System.out.println("for " + ((Move.SingleMove) move).ticket + " to " + ((Move.SingleMove) move).destination + " the score: " + score(board, (Move.SingleMove) move, mrXTickets));
			}
		}
		// pick the best move with the highest score, and return the key for it, so the move itself
		Move bestMove = scoreForMoves.entrySet().stream()
				.max(Map.Entry.comparingByValue())
				.map(Map.Entry::getKey)
				.orElse(null);

		return bestMove;
	}


	private int score(@Nonnull Board board, Move.SingleMove move, ImmutableMap<ScotlandYard.Ticket, Integer> mrXTickets) {
		// The final score of the move
		int score = 0;

		// Adds score for each node adjacent to the destination
		final int CONNECTIVITY = 10;
		// TODO what do you think of using it like this? (score += PENALTY * (average distance before and after move))
		final int PENALTY = 40;
		// Modifies scores based off of distance to (nearest?) detective
		final int[] DISTANCE = new int[]{};
		// (Secret Move) Modify score based on different transport types at source
		final int[] SECRET_CONNECTIVITY = {-25, 5, 25};


		// Increase score by 10 for each adjacent available move at the DESTINATION
		for (int adjacentNode : board.getSetup().graph.adjacentNodes(move.destination)) {
			// First, check if the location is occupied
			if (checkOccupied(board, adjacentNode)) continue;

			// ...and check that Mr. X can actually make that adjacent available move
			// First, get all the possible tickets to use
			ImmutableSet<ScotlandYard.Transport> moves
					= board.getSetup().graph.edgeValueOrDefault(move.source(), move.destination, ImmutableSet.of());
			for (ScotlandYard.Transport transport : moves) {
				if (mrXTickets.get(transport.requiredTicket()) > 0) {
					score += CONNECTIVITY;
					break;	// if mrX has any of the tickets required to get to the destination node, give it the points, don't look further
				}
			}

		}

		// Modify score based on the distances to the detectives compared to the location where mrX starts
		// TODO Evaluate the closest two detectives, which MrX should run from
		// distances at the starting location of the move, the source
		List<Integer> detectiveDistancesSource = detectiveDistances(board, move.source());
		// distances at the destination of the move
		List<Integer> detectiveDistancesDestination = detectiveDistances(board, move.destination);

		double initialDanger = detectiveDistancesSource.stream()
				.mapToDouble(distance -> 1.0 / distance)
				.sum();
		double nextDanger = detectiveDistancesDestination.stream()
				.mapToDouble(distance -> 1.0 / distance)
				.sum();
		double dangerChange = initialDanger - nextDanger;
		score += (int) Math.floor(PENALTY * dangerChange);


		// If the move uses a secret ticket, evaluate its use based off of the
		// NUMBER OF DIFFERENT TYPES of modes of transport available at the SOURCE
		if (move.ticket == ScotlandYard.Ticket.SECRET) {
			HashSet<ScotlandYard.Transport> transports = new HashSet<>();
			// For each possible move, adds the different types of transport to a hash set
			for (int adjacentNode : board.getSetup().graph.adjacentNodes(move.source())) {
				transports.addAll(board.getSetup().graph.edgeValueOrDefault(move.source(), adjacentNode, ImmutableSet.of()));
				// There are only max 3 different transport types, save some time...
				if (transports.size() == 3) break;
			}

			score += SECRET_CONNECTIVITY[transports.size() - 1];
		}

		return score;
	}

//	private int score(@Nonnull Board board, Move.DoubleMove move) {
//		// Slightly discouraged from using a double ticket
//		int score = -5;
//
//		return score;
//	}


	// ---------- Helper Functions ----------
	//


	// Checks if a location is occupied by a detective
	private boolean checkOccupied(@Nonnull Board board, int location) {
		for (Piece piece : board.getPlayers()) {
			if (piece.isDetective()) {
				Optional<Integer> detectiveLocation = board.getDetectiveLocation((Piece.Detective) piece);
				if (detectiveLocation.isPresent() && detectiveLocation.get() == location) return true;
			}
		}
		return false;
	}

	// Returns all detectives as players
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

	// class to hold the nodes that distanceFromDetective searches through, and tickets left
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

	// helper to return new ImmutableMap of TicketBoard that uses the ticket for the transport
	public ImmutableMap<ScotlandYard.Ticket, Integer> useTicket(ImmutableMap<ScotlandYard.Ticket, Integer> tickets, ScotlandYard.Transport transport) {
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

	// helper to convert from TicketBoard into Map<Ticket, Integer>, used in distanceFromDetective
	public ImmutableMap<ScotlandYard.Ticket, Integer> convertTickets(Board.TicketBoard ticketBoard) {
		// map to store the tickets
		Map<ScotlandYard.Ticket, Integer> tickets = new HashMap<>();

		// iterate over all ticket types and add according counts to the map
		for (ScotlandYard.Ticket ticket : ScotlandYard.Ticket.values()) {
			tickets.put(ticket, ticketBoard.getCount(ticket));
		}
		return ImmutableMap.copyOf(tickets);
	}

	// Get the shortest distance from a detective to Mr. X
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
}
