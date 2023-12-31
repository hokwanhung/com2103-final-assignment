import java.io.*;
import java.util.*;

/*
 * Model part seems difficult because it has to get data in other classes, including labels in view
 */

public class model {
	player[] p = new player[4];
	land[] l = new land[40];
	slot[] s = new slot[23];
	int turn = 0;
	int[] slotIndex = { 0, 1, 2, 3, 5, 6, 8, 12, 13, 15, 16, 18, 22, 24, 25, 27, 28, 32, 33, 34, 36, 37, 38 };
	HashMap<Integer, Integer> dict = new HashMap<Integer, Integer>();
	boolean gameover = false;
	controller c;

	// Set up connection with the controller
	public void setController(controller c) {
		this.c = c;
	}

	public void initialize() {
		turn = 0;

		// Initialize all slots
		for (int i = 0; i < 40; i++) {
			l[i] = new land();
			l[i].isSlot = false;
			l[i].ownerID = 4; // owner4 = null
		}

		// Initialize all land slots
		for (int i = 0; i < slotIndex.length; i++) {
			s[i] = l[slotIndex[i]];
			l[slotIndex[i]].isSlot = true;
			dict.put(slotIndex[i], i);
		}

		// Initialize all players
		for (int i = 0; i < 4; i++) {
			p[i] = new player();
			p[i].money = 1000;
			p[i].alive = true;
			p[i].position = 0;
			l[0].playerHere.add("p" + i); // the player id starts from 0
			c.updateMoney(i, p[i].money);
		}

		// Update the four player's on the map
		c.updateMap(0, "p1, p2, p3, p4");

		// Initialize the land slots' info
		try {
			File file = new File("data.csv");
			Scanner sc = new Scanner(file);
			for (int i = 0; sc.hasNext(); i++) {
				String[] line = sc.nextLine().split(",");
				s[i] = new slot();
				s[i].go = false;
				s[i].name = line[0];
				s[i].price = Integer.parseInt(line[1]);
				c.updateSlot(i, s[i].name, s[i].price);
			}
			s[0].go = true;
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public int drawDice() {
		int dice = 0;
		// Only draw a dice if the player is alive,
		if (p[turn].alive == true) {
			// roll a dice between 1 and 10
			dice = 1 + (int) (Math.random() * 10);

			// remove the player's info from the previous land slot (turn+1 = starts from 1)
			// why not just turn, so the stored p0 can be used directly???
			l[p[turn].position].playerHere.remove("p" + (turn + 1));
			try {
				if (l[p[turn].position].playerHere.contains("p0")) {
					l[p[turn].position].playerHere.remove("p0");
					l[p[turn].position].playerHere.add("p4"); // p4 = null
				}
			} catch (Exception e) {

			}

			// update the land slot's playerSum where the current player steps on
			c.updateMap(p[turn].position, String.join(",", l[p[turn].position].playerHere));

			if (p[turn].position + dice > 39) { // if the player passes through slot 0
				p[turn].position = p[turn].position + dice - 40;
				l[p[turn].position].playerHere.add("p" + (turn + 1));
				p[turn].money += 2000;
				c.updateMoney(turn, p[turn].money);
			} else {
				p[turn].position = p[turn].position + dice;
				l[p[turn].position].playerHere.add("p" + (turn + 1));
			}

			// update the land slot's playerSum where the current player steps on AGAIN??
			l[p[turn].position].playerHere.sort(Comparator.naturalOrder());
			String playerList = String.join(",", l[p[turn].position].playerHere);
			c.updateMap(p[turn].position, playerList);

			// update the current player's position on the right panel
			c.locV(p[turn].position, turn);

			if (l[p[turn].position].isSlot) { // if the slot is a land slot,
				if (s[dict.get(p[turn].position)].ownerID == 4 && p[turn].money > s[dict.get(p[turn].position)].price) {
					// if the land slot is unoccupied and the player has enough money,
					c.buyControl(); // he/she has the option to buy the land.
				} else if (s[dict.get(p[turn].position)].ownerID < 4 && s[dict.get(p[turn].position)].ownerID != turn) {
					// if the land slot is occupied and the owner is not the current player,
					payRentalFee();
				} else {
					System.out.printf("Player %s: Safe\n", turn);
					processTurn();
				}
			} else { // if the slot is not a land slot,
				System.out.printf("Player %s: Safe\n", turn);
				processTurn();
			}
		} else { // if the player is not alive,
			processTurn();
		}

		// return the dice number for display purpose
		// if dice=0, it means the player's turn is skipped
		return dice;

	}

	public void payRentalFee() {
		double rentalFee = s[dict.get(p[turn].position)].price * 0.1; // 10% of the land price
		System.out.println("RentalFee: " + rentalFee);
		if (p[turn].money < rentalFee) { // if the player cannot pay the fee,
			declareBankrupcy(true, turn); // declare bankruptcy
		} else { // if the player can pay the fee,
			// the current player pays the rentalFee.
			p[turn].money -= rentalFee;
			System.out.println(p[turn].money);
			c.updateMoney(turn, p[turn].money);
			

			// the landowner gets the rentalFee.
			p[s[dict.get(p[turn].position)].ownerID].money += rentalFee;
			System.out.println(p[s[dict.get(p[turn].position)].ownerID].money);
			c.updateMoney(s[dict.get(p[turn].position)].ownerID, p[s[dict.get(p[turn].position)].ownerID].money);

			processTurn();
		}
	}

	public void doTrade(String slot, String purchase) {
		try {
			int slotIndex = Integer.parseInt(slot);
			int amountPurchase = Integer.parseInt(purchase);
			
			if (s[slotIndex].ownerID != 4) {
				// Add the purchase to the previous owner with the target land slot ownership
				// Reduce the corresponding purchase from the current player
				p[s[slotIndex].ownerID].money += amountPurchase;
				c.updateMoney(s[slotIndex].ownerID, p[s[slotIndex].ownerID].money);
				p[turn].money -= amountPurchase;
				c.updateMoney(turn, p[turn].money);

				// Update the target land slot ownership to current player
				s[slotIndex].ownerID = turn;
				c.updateOwner(slotIndex, turn);
			} else { // if the land has no owner,
				c.displayMessageDialog("The land has no owner!");
				c.resetTradePanel();
			}
		} catch (NumberFormatException e) {
			c.displayMessageDialog("The input slot number and price is in wrong format!");
			c.resetTradePanel();
		}
	}

	public void processBuy() {
		// Use the current turn's player position to get the land index, and thus get
		// the owner.
		// ownerID --> from land extends slot; turn --> 0-3
		// Update stored data (sort of)
		s[dict.get(p[turn].position)].ownerID = turn;
		p[turn].money -= s[dict.get(p[turn].position)].price;
		// update the GUI
		c.updateMoney(turn, p[turn].money);
		c.updateOwner(dict.get(p[turn].position), turn);

		processTurn();
	}

	/*
	 * Reused functions
	 */
	public void processTurn() {
		// Progress the turn by one.
		turn++;
		if (turn == 4) {
			turn = 0;
		}

		// Update the Notice as well.
		c.updateNotice(turn + 1);
	}

	public void declareBankrupcy(boolean isNotCheat, int target) {
		// log
		System.out.printf("Player %s is bankrupted:\n", target + 1);
		// Updating the balance
		if (isNotCheat == true) { // the player goes bankrupt naturally,
			// the remaining balance is transferred.
			p[s[dict.get(p[target].position)].ownerID].money += p[target].money;
			c.updateMoney(s[dict.get(p[target].position)].ownerID, p[s[dict.get(p[target].position)].ownerID].money);
		} else { // the player goes bankrupt by cheat,
			p[target].money = 0; // the remaining balance returns to 0 directly.
		}
		c.updateMoney(target, 0);

		// Updating the status as bankrupted
		p[target].alive = false;
		c.updateStatus(target);

		// Returning all owned lands to null
		for (int i = 1; i < 23; i++) {
			if (s[i].ownerID == target) {
				s[i].ownerID = 4;
				s[i] = l[slotIndex[i]];
				l[slotIndex[i]].isSlot = true;
				dict.put(slotIndex[i], i);
				c.updateOwner(i, 4);
			}
		}

		/*
		 * Updating the player's position
		 */
		// Removing from previous location,
		l[p[target].position].playerHere.remove("p" + (target + 1));
		try {
			if (l[p[target].position].playerHere.contains("p0")) {
				l[p[target].position].playerHere.remove("p0");
				l[p[target].position].playerHere.add("p4"); // p4 = null
			}
		} catch (Exception e) {

		}
		c.updateMap(p[target].position, String.join(",", l[p[target].position].playerHere));
		// Updating to slot 0
		p[target].position = 0;
		l[p[target].position].playerHere.add("p" + (target + 1));
		l[p[target].position].playerHere.sort(Comparator.naturalOrder());

		String playerList = String.join(",", l[p[target].position].playerHere);
		c.updateMap(p[target].position, playerList);
		c.locV(0, target);

		// Display the message that the target player is bankrupted
		c.displayMessageDialog("Player " + (target + 1) + " is bankrupted!");

		processTurn();
	}

	/*
	 * Tester-related functions
	 */

	public void adminPML(int pl, int m, int lc) {
		p[pl].money = m;
		l[p[pl].position].playerHere.remove("p" + (pl + 1));
		try {
			l[p[pl].position].playerHere.remove("p" + 0);
		} catch (Exception e) {
		}
		c.updateMap(p[pl].position, String.join(",", l[p[pl].position].playerHere));
		p[pl].position = lc; // Have to change the player class position attribute
		l[lc].playerHere.add("p" + (pl + 1)); // Have to add back the position in the land slot
		c.updateMoney(pl, m);
		c.updateMap(lc, String.join(",", l[p[pl].position].playerHere));
		c.locV(p[pl].position, pl);
	}

	public void adminSOP(int slotID, int ownerID, int pr) {
		s[slotID].ownerID = ownerID - 1;
		s[slotID].price = pr;
		System.out.println(s[slotID].ownerID);
		System.out.println(s[slotID].price);
		c.updateOwner(slotID, (ownerID - 1));
		c.updatePrice(slotID, pr);
	}

	public void adminT(int t) {
		turn = t;
		c.updateNotice(t + 1);
	}

	public void Status(int target) { // pl is gathered from the admin text-box
		// Get and Set the player's status to the opposite (ALIVE or BANKRUPTED)

		boolean current = p[target].alive;
		if (current == true) { // if player is alive,
			declareBankrupcy(false, target); // then declare bankruptcy by cheat.
		} else { // if the player is already bankrupted.
			// Update the balance back to the initial $1000
			p[target].money = 1000;
			c.updateMoney(target, 1000);

			// Update the status back to "Alive"
			p[target].alive = !current;
			c.updateStatus(target);

			// Update the position on the player's info panel
			p[target].position = 0;
			c.locV(0, target);
		}
	}

}
