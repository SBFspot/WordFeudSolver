package nu.mrpi.wordfeudsolver.chat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import nu.mrpi.wordfeudapi.WordFeudClient;
import nu.mrpi.wordfeudapi.domain.BoardType;
import nu.mrpi.wordfeudapi.domain.Game;
import nu.mrpi.wordfeudapi.domain.RuleSet;
import nu.mrpi.wordfeudsolver.chat.commands.DifficultyCommand;
import nu.mrpi.wordfeudsolver.domain.DifficultyStats;
import nu.mrpi.wordfeudsolver.domain.PlayerStats;
import nu.mrpi.wordfeudsolver.persistance.PlayerNotFoundException;
import nu.mrpi.wordfeudsolver.service.GameService;

/**
 * @author Pierre Ingmansson
 */
public enum ChatCommand {
    Help(false, data -> {
        WordFeudClient client = data.getClient();
        MessageStore messageStore = data.getMessageStore();

        Game game = data.getClient().getGame(data.getGameId());

        client.chat(data.getGameId(), messageStore.getHelp(game.getLanguageLocale()));
    }),
    Status(true, new Command() {
        public void executeCommand(final CommandData data) {
            final WordFeudClient client = data.getClient();

            client.chat(data.getGameId(), buildStatusString(client));
        }

        private String buildStatusString(final WordFeudClient client) {
            final StringBuilder status = new StringBuilder();

            final Game[] games = client.getGames();

            int activeGames = 0;
            int gamesInLead = 0;
            int finishedGames = 0;
            int gamesLost = 0;
            StringBuilder lostTo = new StringBuilder();

            for (final Game game : games) {
                if (game.isRunning()) {
                    activeGames++;

                    if (game.isInLead()) {
                        gamesInLead++;
                    }
                } else {
                    finishedGames++;

                    if (game.isLost()) {
                        gamesLost++;

                        if (lostTo.length() > 0) {
                            lostTo.append(", ").append(game.getOpponentName());
                        } else {
                            lostTo.append(game.getOpponentName());
                        }
                    }
                }
            }

            status.append(activeGames).append(" running games (").append(gamesInLead).append(" in lead), ").append(finishedGames).append(" finished; ");

            if (gamesLost > 0) {
                status.append("won ").append(finishedGames - gamesLost).append(", ");
                status.append("lost ").append(gamesLost).append(" (to ").append(lostTo).append(")");
            } else {
                status.append("won all!");
            }
            status.append(".");

            return status.toString();
        }
    }),

    Statistics(false, data -> {
        final Game game = data.getGame();
        final Locale locale = game.getLanguageLocale();
        final GameService gameService = data.getGameService();
        final WordFeudClient client = data.getClient();
        final MessageStore messageStore = data.getMessageStore();

        try {
            final PlayerStats playerStats = gameService.getPlayerStats(data.getFromUsername());

            client.chat(game, messageStore.getStatsInfoMessage(locale));

            final List<DifficultyStats> difficultyStats = new ArrayList<>(playerStats.gameStats().values());
            Collections.sort(difficultyStats);

            for (DifficultyStats difficultyStat : difficultyStats) {
                client.chat(game, messageStore.getDifficultyStats(locale, difficultyStat));
            }
        } catch (PlayerNotFoundException e) {
            client.chat(game, messageStore.getNoStatsFound(locale));
        }
    }),

    Invite(true, new Command() {
        public void executeCommand(final CommandData data) {
            final WordFeudClient client = data.getClient();

            final String[] tokens = data.getMessage().split("\\s");
            if (tokens.length > 1) {
                RuleSet ruleSet = RuleSet.Swedish;
                BoardType boardType = BoardType.Normal;

                try {
                    if (tokens.length > 2) {
                        ruleSet = RuleSet.fromString(tokens[2]);
                    }
                    if (tokens.length > 3) {
                        boardType = BoardType.fromString(tokens[3]);
                    }

                    String username = tokens[1];
                    client.invite(username, ruleSet, boardType);
                    client.chat(data.getGameId(), "Invite to " + username + " sent!");
                } catch (Exception e) {
                    sendInstructions(data, client);
                }
            } else {
                sendInstructions(data, client);
            }
        }

        private void sendInstructions(final CommandData data, final WordFeudClient client) {
            client.chat(data.getGameId(), "Invite should look like this; \"invite <username> [ruleset] [boardType]\". Ex: \"invite Dude swedish random\"");
        }
    }),

    Surrender(true, data -> { data.getGameService().surrender(data.getGame()); }),

    Difficulty(false, new DifficultyCommand()),

    Easy(false, DifficultyCommand.forLevel(nu.mrpi.wordfeudsolver.domain.Difficulty.EASY)),
    Medium(false, DifficultyCommand.forLevel(nu.mrpi.wordfeudsolver.domain.Difficulty.MEDIUM)),
    Hard(false, DifficultyCommand.forLevel(nu.mrpi.wordfeudsolver.domain.Difficulty.HARD)),
    Nightmare(false, DifficultyCommand.forLevel(nu.mrpi.wordfeudsolver.domain.Difficulty.NIGHTMARE)),
    Shortest(false, DifficultyCommand.forLevel(nu.mrpi.wordfeudsolver.domain.Difficulty.SHORTEST)),
    Longest(false, DifficultyCommand.forLevel(nu.mrpi.wordfeudsolver.domain.Difficulty.LONGEST));

    private final boolean adminCommand;
    private final Command command;

    ChatCommand(final boolean adminCommand, final Command command) {
        this.adminCommand = adminCommand;
        this.command = command;
    }

    public boolean isAdminCommand() {
        return adminCommand;
    }

    public void executeCommand(final CommandData commandData) {
        command.executeCommand(commandData);
    }
}
