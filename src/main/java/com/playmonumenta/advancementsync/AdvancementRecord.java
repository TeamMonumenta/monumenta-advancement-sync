package com.playmonumenta.advancementsync;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.playmonumenta.advancementsync.utils.DataPackUtils;

import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class AdvancementRecord {
	private Instant mInstant = null;
	private Map<String, Set<String>> mFirstPlayerTeams = new HashMap<String, Set<String>>();
	private Map<String, Set<String>> mLaterPlayerTeams = new HashMap<String, Set<String>>();

	class PlayerTeamsMap extends HashMap<String, Set<String>> {
		public PlayerTeamsMap(Map<String, Set<String>> m) {
			super(m);
		}

		public void addAll(Map<String, Set<String>> other) {
			for (Map.Entry<String, Set<String>> entry : other.entrySet()) {
				String teamId = entry.getKey();
				Set<String> otherTeamPlayers = entry.getValue();

				Set<String> thisTeamPlayers = this.get(teamId);
				if (thisTeamPlayers == null) {
					thisTeamPlayers = new HashSet<String>(otherTeamPlayers);
					this.put(teamId, thisTeamPlayers);
				} else {
					thisTeamPlayers.addAll(otherTeamPlayers);
				}
			}
		}

		public void removeAll(Map<String, Set<String>> other) {
			Set<String> teamsToRemove = new HashSet<String>();
			for (Map.Entry<String, Set<String>> entry : this.entrySet()) {
				String teamId = entry.getKey();
				Set<String> thisTeamPlayers = entry.getValue();

				Set<String> otherTeamPlayers = this.get(teamId);
				if (otherTeamPlayers != null) {
					thisTeamPlayers.removeAll(otherTeamPlayers);
					if (thisTeamPlayers.isEmpty()) {
						teamsToRemove.add(teamId);
					}
				}
			}

			for (String teamId : teamsToRemove) {
				this.remove(teamId);
			}
		}
	}

	public AdvancementRecord(AdvancementRecord toClone) {
		mInstant = toClone.getInstant();
		mFirstPlayerTeams = toClone.getFirstPlayerTeams();
		mLaterPlayerTeams = toClone.getLaterPlayerTeams();
	}

	public AdvancementRecord(Player player, Advancement advancement) {
		mInstant = DataPackUtils.getEarnedInstant(player, advancement);

		String playerName = player.getName();
		String playerTeam = "NoTeam";
		if (DataPackUtils.getPlayerTeam(player) != null) {
			playerTeam = DataPackUtils.getPlayerTeam(player).getName();
		}
		Set<String> playerNames = mFirstPlayerTeams.get(playerTeam);
		if (playerNames == null) {
			playerNames = new HashSet<String>();
			playerNames.add(playerName);
			mFirstPlayerTeams.put(playerTeam, playerNames);
		} else {
			playerNames.add(playerName);
		}
	}

	public AdvancementRecord(JsonObject record) throws Exception {
		if (record == null) {
			throw new Exception("record was null");
		}

		JsonPrimitive instantPrimitive = record.getAsJsonPrimitive("instant");
		JsonObject firstPlayerTeamsObject = record.getAsJsonObject("first_player_teams");
		JsonObject laterPlayerTeamsObject = record.getAsJsonObject("later_player_teams");

		try {
			Long instantMs = instantPrimitive.getAsLong();
			mInstant = Instant.ofEpochMilli(instantMs);

			String teamId;
			Set<String> playerNames;

			for (Map.Entry<String, JsonElement> entry : firstPlayerTeamsObject.entrySet()) {
				teamId = entry.getKey();
				playerNames = new HashSet<String>();
				for (JsonElement playerNameElement : entry.getValue().getAsJsonArray()) {
					playerNames.add(playerNameElement.getAsString());
				}
				mFirstPlayerTeams.put(teamId, playerNames);
			}

			for (Map.Entry<String, JsonElement> entry : laterPlayerTeamsObject.entrySet()) {
				teamId = entry.getKey();
				playerNames = new HashSet<String>();
				for (JsonElement playerNameElement : entry.getValue().getAsJsonArray()) {
					playerNames.add(playerNameElement.getAsString());
				}
				mLaterPlayerTeams.put(teamId, playerNames);
			}
		} catch (Exception e) {
			throw new Exception("json is not in record format");
		}

		if (mInstant == null ||
		    mFirstPlayerTeams.isEmpty()) {
			throw new Exception("json is not in record format");
		}
	}

	public Instant getInstant() {
		return Instant.ofEpochMilli(mInstant.toEpochMilli());
	}

	public Map<String, Set<String>> getFirstPlayerTeams() {
		Map<String, Set<String>> result = new HashMap<String, Set<String>>();
		for (Map.Entry<String, Set<String>> entry : mFirstPlayerTeams.entrySet()) {
			result.put(entry.getKey(), new HashSet<String>(entry.getValue()));
		}
		return result;
	}

	public Map<String, Set<String>> getLaterPlayerTeams() {
		Map<String, Set<String>> result = new HashMap<String, Set<String>>();
		for (Map.Entry<String, Set<String>> entry : mLaterPlayerTeams.entrySet()) {
			result.put(entry.getKey(), new HashSet<String>(entry.getValue()));
		}
		return result;
	}

	public Set<Map.Entry<String, Set<String>>> getNewlyFirstPlayers(AdvancementRecord oldRecord) {
		if (oldRecord == null) {
			return getFirstPlayerTeams().entrySet();
		}
		PlayerTeamsMap newlyFirstPlayers;

		int oldComparedToCurrentRecord = oldRecord.mInstant.compareTo(mInstant);
		if (oldComparedToCurrentRecord < 0) {
			// Old record was faster, no newly faster records
			return new HashMap<String, Set<String>>().entrySet();
		}

		// Current record was at least as fast, so all current first records are at least as fast...
		newlyFirstPlayers = new PlayerTeamsMap(getFirstPlayerTeams());

		// ...and old ones aren't "newly faster"
		newlyFirstPlayers.removeAll(oldRecord.getFirstPlayerTeams());

		return newlyFirstPlayers.entrySet();
	}

	public Set<Map.Entry<String, Set<String>>> getNewlyLaterPlayers(AdvancementRecord oldRecord) {
		if (oldRecord == null) {
			return getLaterPlayerTeams().entrySet();
		}
		PlayerTeamsMap newlyLaterPlayers;

		int oldComparedToCurrentRecord = oldRecord.mInstant.compareTo(mInstant);
		if (oldComparedToCurrentRecord < 0) {
			// Old was faster, so all the entries from the current record are later
			newlyLaterPlayers = new PlayerTeamsMap(getFirstPlayerTeams());
			newlyLaterPlayers.addAll(getLaterPlayerTeams());

			// But, anything old is already accounted for, not "newly later"
			newlyLaterPlayers.removeAll(oldRecord.getFirstPlayerTeams());
			newlyLaterPlayers.removeAll(oldRecord.getLaterPlayerTeams());

			return newlyLaterPlayers.entrySet();
		}

		// If the current record is as fast or faster than the old record, the logic is the same.

		// Any current later entries are newly later...
		newlyLaterPlayers = new PlayerTeamsMap(getLaterPlayerTeams());

		// ...unless they're accounted for in the old record...
		newlyLaterPlayers.removeAll(oldRecord.getLaterPlayerTeams());

		// ...or corrected to later times. Handle those separately.
		newlyLaterPlayers.removeAll(oldRecord.getFirstPlayerTeams());

		return newlyLaterPlayers.entrySet();
	}

	public Set<Map.Entry<String, Set<String>>> getCorrectedPlayers(AdvancementRecord oldRecord) {
		if (oldRecord == null) {
			return new HashMap<String, Set<String>>().entrySet();
		}
		PlayerTeamsMap correctedLaterPlayers;

		int oldComparedToCurrentRecord = oldRecord.mInstant.compareTo(mInstant);
		if (oldComparedToCurrentRecord < 0) {
			// Old was faster, so nothing to do.
			return new HashMap<String, Set<String>>().entrySet();
		} else if (oldComparedToCurrentRecord == 0) {
			// Same time! Return first entries from the old record that are also a later entry of the current record.
			Map<String, Set<String>> currentLaterEntries = getLaterPlayerTeams();

			Map<String, Set<String>> entrySetGetter = new HashMap<String, Set<String>>();
			for (Map.Entry<String, Set<String>> entry : oldRecord.getFirstPlayerTeams().entrySet()) {
				String teamId = entry.getKey();
				Set<String> currentlyLaterPlayers = currentLaterEntries.get(teamId);
				if (currentlyLaterPlayers != null) {
					Set<String> correctedPlayers = new HashSet<String>(entry.getValue());
					correctedPlayers.retainAll(currentlyLaterPlayers);
					entrySetGetter.put(teamId, correctedPlayers);
				}
			}
			correctedLaterPlayers = new PlayerTeamsMap(entrySetGetter);
		} else {
			// Current record was faster. Old first entries are "corrected later"...
			correctedLaterPlayers = new PlayerTeamsMap(oldRecord.getFirstPlayerTeams());

			// ...assuming they're not also fastest on the current record.
			correctedLaterPlayers.removeAll(getFirstPlayerTeams());
		}

		return correctedLaterPlayers.entrySet();
	}

	public Set<String> getNewlyFirstTeams(AdvancementRecord oldRecord) {
		if (oldRecord == null) {
			return new HashSet<String>(getFirstPlayerTeams().keySet());
		}
		Set<String> newlyFirstTeams;

		int oldComparedToCurrentRecord = oldRecord.mInstant.compareTo(mInstant);
		if (oldComparedToCurrentRecord < 0) {
			// Old record was faster, no newly faster records
			return new HashSet<String>();
		}

		// Current record was at least as fast, so all current first records are at least as fast...
		newlyFirstTeams = new HashSet<String>(getFirstPlayerTeams().keySet());

		// ...and old ones aren't "newly faster"
		newlyFirstTeams.removeAll(oldRecord.getFirstPlayerTeams().keySet());

		return newlyFirstTeams;
	}

	public Set<String> getNewlyLaterTeams(AdvancementRecord oldRecord) {
		Set<String> newlyLaterTeams;
		if (oldRecord == null) {
			newlyLaterTeams = new HashSet<String>(getLaterPlayerTeams().keySet());
			newlyLaterTeams.removeAll(getFirstPlayerTeams().keySet());
			return newlyLaterTeams;
		}

		int oldComparedToCurrentRecord = oldRecord.mInstant.compareTo(mInstant);
		if (oldComparedToCurrentRecord < 0) {
			// Old was faster, so all the entries from the current record are later
			newlyLaterTeams = new HashSet<String>(getFirstPlayerTeams().keySet());
			newlyLaterTeams.addAll(getLaterPlayerTeams().keySet());

			// But, anything old is already accounted for, not "newly later"
			newlyLaterTeams.removeAll(oldRecord.getFirstPlayerTeams().keySet());
			newlyLaterTeams.removeAll(oldRecord.getLaterPlayerTeams().keySet());

			return newlyLaterTeams;
		}

		// If the current record is as fast or faster than the old record, the logic is the same.

		// Any current later entries are newly later...
		newlyLaterTeams = new HashSet<String>(getLaterPlayerTeams().keySet());

		// ...unless the same team is still in first...
		newlyLaterTeams.removeAll(getFirstPlayerTeams().keySet());

		// ...they're accounted for in the old record...
		newlyLaterTeams.removeAll(oldRecord.getLaterPlayerTeams().keySet());

		// ...or corrected to later times. Handle those separately.
		newlyLaterTeams.removeAll(oldRecord.getFirstPlayerTeams().keySet());

		return newlyLaterTeams;
	}

	public Set<String> getCorrectedTeams(AdvancementRecord oldRecord) {
		if (oldRecord == null) {
			return new HashSet<String>();
		}
		Set<String> correctedLaterTeams;

		int oldComparedToCurrentRecord = oldRecord.mInstant.compareTo(mInstant);
		if (oldComparedToCurrentRecord < 0) {
			// Old was faster, so nothing to do.
			return new HashSet<String>();
		} else if (oldComparedToCurrentRecord == 0) {
			// Same time! Return first teams from the current record that are also a later teams of the old record...
			correctedLaterTeams = new HashSet<String>(oldRecord.getLaterPlayerTeams().keySet());
			correctedLaterTeams.retainAll(getFirstPlayerTeams().keySet());

			// ...but also remove teams that were first already.
			correctedLaterTeams.removeAll(oldRecord.getFirstPlayerTeams().keySet());
		} else {
			// Current record was faster. Old first entries are "corrected later"...
			correctedLaterTeams = new HashSet<String>(oldRecord.getFirstPlayerTeams().keySet());

			// ...assuming they're not also fastest on the current record.
			correctedLaterTeams.removeAll(getFirstPlayerTeams().keySet());
		}

		return correctedLaterTeams;
	}

	public AdvancementRecord cloneAndUpdate(AdvancementRecord newRecord) {
		AdvancementRecord updatedClone = new AdvancementRecord(this);

		if (newRecord == null) {
			return updatedClone;
		}

		Set<String> teamIds = new HashSet<String>(newRecord.getFirstPlayerTeams().keySet());
		teamIds.addAll(newRecord.getLaterPlayerTeams().keySet());
		teamIds.addAll(updatedClone.getFirstPlayerTeams().keySet());
		teamIds.addAll(updatedClone.getLaterPlayerTeams().keySet());

		Set<String> firstTeamPlayers;
		Set<String> laterTeamPlayers;
		Set<String> newFirstTeamPlayers;
		Set<String> newLaterTeamPlayers;

		int currentComparedToNewRecord = mInstant.compareTo(newRecord.getInstant());
		if (currentComparedToNewRecord < 0) {
			// Current was faster.
			for (String teamId : teamIds) {
				firstTeamPlayers = updatedClone.mFirstPlayerTeams.get(teamId);
				laterTeamPlayers = updatedClone.mLaterPlayerTeams.get(teamId);
				newFirstTeamPlayers = newRecord.mFirstPlayerTeams.get(teamId);
				newLaterTeamPlayers = newRecord.mLaterPlayerTeams.get(teamId);

				if (firstTeamPlayers == null) {
					firstTeamPlayers = new HashSet<String>();
				}
				if (laterTeamPlayers == null) {
					laterTeamPlayers = new HashSet<String>();
				}
				if (newFirstTeamPlayers == null) {
					newFirstTeamPlayers = new HashSet<String>();
				}
				if (newLaterTeamPlayers == null) {
					newLaterTeamPlayers = new HashSet<String>();
				}

				// Update current later to include new first and later...
				laterTeamPlayers.addAll(newFirstTeamPlayers);
				laterTeamPlayers.addAll(newLaterTeamPlayers);

				// ...for those not present in current first.
				laterTeamPlayers.removeAll(firstTeamPlayers);

				if (!laterTeamPlayers.isEmpty()) {
					updatedClone.mLaterPlayerTeams.put(teamId, laterTeamPlayers);
				} else {
					updatedClone.mLaterPlayerTeams.remove(teamId);
				}
			}
		} else if (currentComparedToNewRecord == 0) {
			// Same time!
			for (String teamId : teamIds) {
				firstTeamPlayers = updatedClone.mFirstPlayerTeams.get(teamId);
				laterTeamPlayers = updatedClone.mLaterPlayerTeams.get(teamId);
				newFirstTeamPlayers = newRecord.mFirstPlayerTeams.get(teamId);
				newLaterTeamPlayers = newRecord.mLaterPlayerTeams.get(teamId);

				if (firstTeamPlayers == null) {
					firstTeamPlayers = new HashSet<String>();
				}
				if (laterTeamPlayers == null) {
					laterTeamPlayers = new HashSet<String>();
				}
				if (newFirstTeamPlayers == null) {
					newFirstTeamPlayers = new HashSet<String>();
				}
				if (newLaterTeamPlayers == null) {
					newLaterTeamPlayers = new HashSet<String>();
				}

				// Add all first entries from new record...
				firstTeamPlayers.addAll(newFirstTeamPlayers);

				// ...add all later entries from new record...
				laterTeamPlayers.addAll(newLaterTeamPlayers);

				// ...and remove any later entries that are first in the new record.
				laterTeamPlayers.removeAll(newFirstTeamPlayers);

				if (!firstTeamPlayers.isEmpty()) {
					updatedClone.mFirstPlayerTeams.put(teamId, firstTeamPlayers);
				} else {
					updatedClone.mFirstPlayerTeams.remove(teamId);
				}
				if (!laterTeamPlayers.isEmpty()) {
					updatedClone.mLaterPlayerTeams.put(teamId, laterTeamPlayers);
				} else {
					updatedClone.mLaterPlayerTeams.remove(teamId);
				}
			}
		} else {
			// New record was faster.

			// Update the instant...
			updatedClone.mInstant = newRecord.getInstant();

			for (String teamId : teamIds) {
				firstTeamPlayers = updatedClone.mFirstPlayerTeams.get(teamId);
				laterTeamPlayers = updatedClone.mLaterPlayerTeams.get(teamId);
				newFirstTeamPlayers = newRecord.mFirstPlayerTeams.get(teamId);
				newLaterTeamPlayers = newRecord.mLaterPlayerTeams.get(teamId);

				if (firstTeamPlayers == null) {
					firstTeamPlayers = new HashSet<String>();
				}
				if (laterTeamPlayers == null) {
					laterTeamPlayers = new HashSet<String>();
				}
				if (newFirstTeamPlayers == null) {
					newFirstTeamPlayers = new HashSet<String>();
				}
				if (newLaterTeamPlayers == null) {
					newLaterTeamPlayers = new HashSet<String>();
				}

				// ...copy current first and new later to later...
				laterTeamPlayers.addAll(firstTeamPlayers);
				laterTeamPlayers.addAll(newLaterTeamPlayers);

				// ...and replace updated first with new first
				firstTeamPlayers = new HashSet<String>(newFirstTeamPlayers);

				if (!firstTeamPlayers.isEmpty()) {
					updatedClone.mFirstPlayerTeams.put(teamId, firstTeamPlayers);
				} else {
					updatedClone.mFirstPlayerTeams.remove(teamId);
				}
				if (!laterTeamPlayers.isEmpty()) {
					updatedClone.mLaterPlayerTeams.put(teamId, laterTeamPlayers);
				} else {
					updatedClone.mLaterPlayerTeams.remove(teamId);
				}
			}
		}

		return updatedClone;
	}

	public JsonObject toJson() {
		String teamId;
		JsonArray teamPlayers;

		JsonObject firstPlayerTeams = new JsonObject();
		for (Map.Entry<String, Set<String>> entry : mFirstPlayerTeams.entrySet()) {
			teamId = entry.getKey();
			teamPlayers = new JsonArray();
			for (String player : entry.getValue()) {
				teamPlayers.add(player);
			}
			firstPlayerTeams.add(teamId, teamPlayers);
		}

		JsonObject laterPlayerTeams = new JsonObject();
		for (Map.Entry<String, Set<String>> entry : mLaterPlayerTeams.entrySet()) {
			teamId = entry.getKey();
			teamPlayers = new JsonArray();
			for (String player : entry.getValue()) {
				teamPlayers.add(player);
			}
			laterPlayerTeams.add(teamId, teamPlayers);
		}

		JsonObject record = new JsonObject();

		record.addProperty("instant", mInstant.toEpochMilli());
		record.add("first_player_teams", firstPlayerTeams);
		record.add("later_player_teams", laterPlayerTeams);

		return record;
	}
}
