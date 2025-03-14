package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableSet;
import impl.org.controlsfx.tools.rectangle.change.NewChangeStrategy;
import io.atlassian.fugue.Pair;
import org.glassfish.grizzly.Transport;
import uk.ac.bris.cs.scotlandyard.model.*;

public class Filipek implements Ai {

	@Nonnull @Override
	public String name() { return "Filipek"; }

	@Nonnull @Override
	public Move pickMove(
			@Nonnull Board board,
			Pair<Long, TimeUnit> timeoutPair) {
		// returns a random move, replace with your own implementation
		var moves = board.getAvailableMoves().asList();
		return moves.get(new Random().nextInt(moves.size()));
	}

	private int score(@Nonnull Board board, Move.SingleMove move) {
		// The final score of the move
		int score = 0;

		// Adds score for each node adjacent to the destination
		final int CONNECTIVITY = 10;
		// TODO Create a suitable scoring function/whatever for the distance to the location/Mr. X
		// Modifies scores based off of distance to (nearest?) detective
		final int[] DISTANCE = new int[]{};
		// (Secret Move) Modify score based on different transport types at source
		final int[] SECRET_CONNECTIVITY = {-25, 5, 25};


		// Increase score by 10 for each adjacent available move at the DESTINATION
		for (int adjacentNode : board.getSetup().graph.adjacentNodes(move.destination)) {
			// First, check if the location is occupied
			if (checkOccupied(board, adjacentNode)) continue;

			// ...and check that the detective can actually make that adjacent available move
			ImmutableSet<ScotlandYard.Transport> moves
					= board.getSetup().graph.edgeValueOrDefault(move.source(), move.destination, ImmutableSet.of());
			if (!moves.isEmpty()) score += CONNECTIVITY;
		}

		// Modify score based on the distance to the nearest detective
		// TODO Evaluate the minimum distance from a detective/two detectives



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

	private int score(@Nonnull Board board, Move.DoubleMove move) {
		// Slightly discouraged from using a double ticket
		int score = -5;

		return score;
	}


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

	// Get the shortest distance from a detective to Mr. X
	// TODO Add a way of keeping track of the tickets, possibly by adding a ticket board to the pairs
	public int distanceFromDetective(@Nonnull Board board, Player detective, Player mrX) {
		// Source and target nodes
		int source = detective.location();
		int target = mrX.location();

		// Visited nodes
		HashSet<Integer> visited = new HashSet<>();
		// Distance of each node from source
		HashMap<Integer, Integer> distances = new HashMap<>();
		// Queue of nodes to be visited
		Queue<Pair<Integer, Integer>> queue = new LinkedList<>();

		// Initialise all distances with infinity initially (no null values)
		board.getSetup().graph.nodes().forEach(location -> distances.put(location, Integer.MAX_VALUE));


		// set the source (detective location) to have distance 0 to itself
		distances.replace(source, 0);
		// Add the source's adjacent nodes to the queue, and mark it as visited
		for (int adjacentNode : board.getSetup().graph.adjacentNodes(source)) {
			queue.add(Pair.pair(adjacentNode, source));
		}
		visited.add(source);

		while (!queue.isEmpty()) {
			// Get next node off of queue
			Pair<Integer,Integer> current = queue.poll();
			// Update the distance to the current node
			int minDistance = Math.min(distances.get(current.left()), distances.get(current.right()) + 1);
			distances.replace(current.left(), minDistance);

			// TODO might need to change :( (or not??)
			// If we've reached our target, return the distance to it
			if (current.left() == target) {
				return distances.get(current.left());
			}

			// If the node has not yet been visited, add adjacent nodes to the queue and mark as visited
			if (!visited.contains(current.left())) {
				for (int adjacentNode : board.getSetup().graph.adjacentNodes(current.left())) {
					queue.add(Pair.pair(adjacentNode, current.left()));
				}
				visited.add(current.left());
			}

		}

		// TODO might also need changing??????
		// Otherwise, target not found - return 0
		return 0;
	}
}
