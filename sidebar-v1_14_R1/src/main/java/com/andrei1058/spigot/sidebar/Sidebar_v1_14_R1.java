package com.andrei1058.spigot.sidebar;

import net.minecraft.server.v1_14_R1.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.craftbukkit.v1_14_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Sidebar_v1_14_R1 implements com.andrei1058.spigot.sidebar.Sidebar {

    private final LinkedList<ScoreLine> lines = new LinkedList<>();
    public LinkedList<PlayerConnection> players = new LinkedList<>();
    private final LinkedList<PlaceholderProvider> placeholderProviders = new LinkedList<>();
    private final LinkedList<String> availableColors = new LinkedList<>();
    protected SidebarObjective healthObjective = null;
    private final ConcurrentHashMap<String, PlayerList_v1_14_R1> teamLists = new ConcurrentHashMap<>();

    private final SidebarObjective sidebarObjective;

    public Sidebar_v1_14_R1(@NotNull SidebarLine title, @NotNull Collection<SidebarLine> lines, Collection<PlaceholderProvider> placeholderProvider) {
        for (ChatColor chatColor : ChatColor.values()) {
            this.availableColors.add(chatColor.toString());
        }
        this.sidebarObjective = new SidebarObjective("Sidebar", IScoreboardCriteria.DUMMY, title, 1);
        this.placeholderProviders.addAll(placeholderProvider);
        for (SidebarLine l : lines) {
            addLine(l);
        }
    }


    @Override
    public void setTitle(SidebarLine title) {
        this.sidebarObjective.displayName = title;
        this.sidebarObjective.sendUpdate();
    }

    @Override
    public void addPlaceholder(PlaceholderProvider placeholderProvider) {
        placeholderProviders.remove(placeholderProvider);
        placeholderProviders.add(placeholderProvider);
        lines.forEach(c -> {
            if (!c.text.isHasPlaceholders()) {
                if (c.text instanceof SidebarLineAnimated) {
                    for (String line : ((SidebarLineAnimated) c.text).getLines()) {
                        if (line.contains(placeholderProvider.getPlaceholder())) {
                            c.text.setHasPlaceholders(true);
                            break;
                        }
                    }
                } else if (c.text.getLine().contains(placeholderProvider.getPlaceholder())) {
                    c.text.setHasPlaceholders(true);
                }
            }
        });
    }

    public void addLine(SidebarLine sidebarLine) {
        int score = getAvailableScore();
        if (score == -1) return;
        scoreOffsetIncrease(this.lines);
        String color = availableColors.get(0);
        availableColors.remove(0);
        ScoreLine s = new ScoreLine(sidebarLine, score == 0 ? score : score - 1, color);
        s.sendCreate();
        this.lines.add(s);
        order();
    }

    @Override
    public void setLine(SidebarLine sidebarLine, int line) {
        if (line >= 0 && line < this.lines.size()) {
            ScoreLine s = this.lines.get(line);
            placeholderProviders.forEach(c -> {
                if (sidebarLine.getLine().contains(c.getPlaceholder())) {
                    sidebarLine.setHasPlaceholders(true);
                }
            });
            s.setText(sidebarLine);
        }
    }

    /**
     * @return -1 if no more lines can be added.
     */
    private int getAvailableScore() {
        if (this.lines.isEmpty()) return 0;
        if (this.lines.size() == 16) return -1;
        return this.lines.getFirst().getScore();
    }

    // used when adding/ removing a line
    private void order() {
        Collections.sort(this.lines);
    }

    @Override
    public void apply(Player player) {
        PlayerConnection playerConnection = ((CraftPlayer) player).getHandle().playerConnection;
        sidebarObjective.sendCreate(playerConnection);
        this.lines.forEach(c -> c.sendCreate(playerConnection));
        this.players.add(playerConnection);
        if (healthObjective != null) {
            healthObjective.sendCreate(playerConnection);
            teamLists.forEach((c2, c) -> c.sendCreate(playerConnection));
        }
    }

    public void refreshPlaceholders() {
        this.lines.forEach(s -> {
            if (s.text.isHasPlaceholders()) {
                String content = s.text.getLine();
                for (PlaceholderProvider pp : this.placeholderProviders) {
                    if (content.contains(pp.getPlaceholder())) {
                        content = content.replace(pp.getPlaceholder(), pp.getReplacement());
                    }
                }
                s.setContent(content);
                s.sendUpdate();
            }
        });
    }

    @Override
    public void refreshTitle() {
        this.sidebarObjective.sendUpdate();
    }

    @Override
    public void refreshAnimatedLines() {
        lines.forEach(c -> {
            if (c.text instanceof SidebarLineAnimated) {
                if (c.text.isHasPlaceholders()) {
                    String content = c.text.getLine();
                    for (PlaceholderProvider pp : this.placeholderProviders) {
                        if (content.contains(pp.getPlaceholder())) {
                            content = content.replace(pp.getPlaceholder(), pp.getReplacement());
                        }
                    }
                    c.setContent(content);
                } else {
                    c.setContent(c.text.getLine());
                }
                c.sendUpdate();
            }
        });
    }

    @Override
    public void removeLine(int line) {
        if (line >= 0 && line < this.lines.size()) {
            ScoreLine scoreLine = this.lines.get(line);
            scoreLine.remove();
            this.lines.remove(line);
            scoreOffsetDecrease(this.lines.subList(line, this.lines.size()));
        }
    }

    @Override
    public int linesAmount() {
        return lines.size();
    }

    @Override
    public void removePlaceholder(String placeholder) {
        placeholderProviders.removeIf(p -> p.getPlaceholder().equalsIgnoreCase(placeholder));
    }

    @Override
    public List<PlaceholderProvider> getPlaceholders() {
        return Collections.unmodifiableList(placeholderProviders);
    }

    @Override
    public void playerListCreate(Player player, SidebarLine prefix, SidebarLine suffix, boolean disablePushing) {
        this.playerListRemove(player.getName());

        PlayerList_v1_14_R1 team = new PlayerList_v1_14_R1(this, player, prefix, suffix, disablePushing);
        players.forEach(team::sendCreate);
        teamLists.put(player.getName(), team);
    }

    @Override
    public void playerListAddPlaceholders(Player player, PlaceholderProvider[] placeholderProviders) {
        PlayerList_v1_14_R1 list = teamLists.getOrDefault(player.getName(), null);
        if (list == null) return;
        for (PlaceholderProvider placeholderProvider : placeholderProviders) {
            list.addPlaceholderProvider(placeholderProvider);
        }
        list.sendUpdate();
    }

    @Override
    public void playerListRemovePlaceholder(Player player, String placeholder) {
        PlayerList_v1_14_R1 list = teamLists.getOrDefault(player.getName(), null);
        if (list == null) return;
        list.removePlaceholderProvider(placeholder);
        list.sendUpdate();
    }

    @Override
    public void playerListRemove(String teamName) {
        PlayerList_v1_14_R1 list = teamLists.remove(teamName);
        if (list != null) {
            players.forEach(list::sendRemove);
        }
    }

    @Override
    public void playerListClear() {
        teamLists.forEach((a, b) -> players.forEach(b::sendRemove));
        teamLists.clear();
    }

    @Override
    public void playerListHideNameTag(@NotNull Player player) {
        PlayerList_v1_14_R1 listed = teamLists.get(player.getName());
        if (listed != null) {
            listed.hideNameTag();
        }
    }

    @Override
    public void playerListRestoreNameTag(@NotNull Player player) {
        PlayerList_v1_14_R1 listed = teamLists.get(player.getName());
        if (listed != null) {
            listed.showNameTag();
        }
    }


    @Override
    public void showPlayersHealth(SidebarLine displayName, boolean list) {
        if (healthObjective == null) {
            healthObjective = new SidebarObjective(list ? "health" : "health2", IScoreboardCriteria.HEALTH, displayName, 2);
            players.forEach(c -> healthObjective.sendCreate(c));
        } else {
            healthObjective.sendUpdate();
        }
    }

    @Override
    public void hidePlayersHealth() {
        if (healthObjective != null) {
            players.forEach(c -> healthObjective.sendRemove(c));
            healthObjective = null;
        }
    }

    @Override
    public void refreshHealthAnimation() {
        if (healthObjective != null) {
            if (healthObjective.displayName instanceof SidebarLineAnimated) {
                healthObjective.sendUpdate();
            }
        }
    }

    @Override
    public void refreshHealth(Player player, int health) {
        if (health < 0) {
            health = 0;
        }
        CustomScore_v1_14_R1.sendScore(this, player.getName(), health);
    }

    @Override
    public void playerListRefreshAnimation() {
        teamLists.forEach((a, b) -> b.sendUpdate());
    }

    @Override
    public void remove(UUID player) {
        this.players.removeIf(p -> p.player.getUniqueID().equals(player));
        Player p = Bukkit.getPlayer(player);
        if (p != null) {
            PlayerConnection playerConnection = ((CraftPlayer) p).getHandle().playerConnection;
            teamLists.forEach((b, c) -> c.sendRemove(playerConnection));
            lines.forEach(line -> line.sendRemove(playerConnection));
            this.sidebarObjective.sendRemove(playerConnection);
            if (this.healthObjective != null) {
                this.healthObjective.sendRemove(playerConnection);
            }
        }
    }

    // sends score update packet
    // used when adding a line
    private static void scoreOffsetIncrease(@NotNull Collection<ScoreLine> lineCollections) {
        lineCollections.forEach(c -> c.setScore(c.getScore() + 1));
    }

    // sends score update
    // used when removing a line
    private static void scoreOffsetDecrease(@NotNull Collection<ScoreLine> lineCollections) {
        lineCollections.forEach(c -> c.setScore(c.getScore() - 1));
    }

    protected class SidebarObjective extends ScoreboardObjective {

        private SidebarLine displayName;
        private final int type;

        public SidebarObjective(String name, IScoreboardCriteria criteria, SidebarLine displayName, int type) {
            super(null, name, criteria, new ChatComponentText(name), IScoreboardCriteria.EnumScoreboardHealthDisplay.INTEGER);
            this.displayName = displayName;
            this.type = type;
        }

        @Override
        public String getName() {
            return super.getName();
        }

        @Override
        public IChatBaseComponent getDisplayName() {
            String t = displayName.getLine();
            if (t.length() > 16) {
                t = t.substring(0, 16);
            }
            return new ChatComponentText(t);
        }

        @Override
        public void setDisplayName(IChatBaseComponent var0) {
        }

        @Override
        public IChatBaseComponent e() {
            //return ChatComponentUtils.a(this.getDisplayName().h().a((var0) -> var0.setChatHoverable(new ChatHoverable(ChatHoverable.EnumHoverAction.SHOW_TEXT, new ChatComponentText(this.getName())))));
            return new ChatComponentText(this.getDisplayName().getText());
        }

        @Override
        public void setRenderType(IScoreboardCriteria.EnumScoreboardHealthDisplay var0) {
        }

        private void sendCreate(@NotNull PlayerConnection playerConnection) {
            PacketPlayOutScoreboardObjective packetPlayOutScoreboardObjective = new PacketPlayOutScoreboardObjective(this, 0);
            playerConnection.sendPacket(packetPlayOutScoreboardObjective);
            PacketPlayOutScoreboardDisplayObjective packetPlayOutScoreboardDisplayObjective = new PacketPlayOutScoreboardDisplayObjective(type, this);
            playerConnection.sendPacket(packetPlayOutScoreboardDisplayObjective);
            if (getName().equalsIgnoreCase("health")) {
                PacketPlayOutScoreboardDisplayObjective packetPlayOutScoreboardDisplayObjective2 = new PacketPlayOutScoreboardDisplayObjective(0, this);
                playerConnection.sendPacket(packetPlayOutScoreboardDisplayObjective2);
            }
        }

        // must be called when updating the name
        private void sendUpdate() {
            PacketPlayOutScoreboardObjective packetPlayOutScoreboardObjective = new PacketPlayOutScoreboardObjective(this, 2);
            players.forEach(c -> c.sendPacket(packetPlayOutScoreboardObjective));
        }

        public void sendRemove(@NotNull PlayerConnection playerConnection) {
            PacketPlayOutScoreboardObjective packetPlayOutScoreboardObjective = new PacketPlayOutScoreboardObjective(this, 1);
            playerConnection.sendPacket(packetPlayOutScoreboardObjective);
        }
    }

    private class ScoreLine extends ScoreboardScore implements Comparable<ScoreLine> {

        private int score;
        private String prefix = " ", suffix = "";
        private TeamLine team;
        private SidebarLine text;

        public ScoreLine(@NotNull SidebarLine text, int score, @NotNull String color) {
            super(null, sidebarObjective, color);
            this.score = score;
            this.text = text;
            this.team = new TeamLine(color);

            if (!text.isHasPlaceholders()) {
                placeholderProviders.forEach(c -> {
                    if (text.getLine().contains(c.getPlaceholder())) {
                        text.setHasPlaceholders(true);
                    }
                });

                if (!text.isHasPlaceholders()) {
                    if (text instanceof SidebarLineAnimated) {
                        for (String line : ((SidebarLineAnimated) text).getLines()) {
                            if (SidebarManager.getPapiSupport().hasPlaceholders(line)) {
                                text.setHasPlaceholders(true);
                                break;
                            }
                        }
                    } else if (SidebarManager.getPapiSupport().hasPlaceholders(text.getLine())) {
                        text.setHasPlaceholders(true);
                    }
                }
            }

            if (text.isHasPlaceholders()) {
                String content = text.getLine();
                for (PlaceholderProvider pp : placeholderProviders) {
                    if (content.contains(pp.getPlaceholder())) {
                        content = content.replace(pp.getPlaceholder(), pp.getReplacement());
                    }
                }
                setContent(content);
            } else {
                setContent(text.getLine());
            }
        }

        private void setText(@NotNull SidebarLine text) {
            if (!text.isHasPlaceholders()) {
                if (text instanceof SidebarLineAnimated) {
                    for (String line : ((SidebarLineAnimated) text).getLines()) {
                        if (SidebarManager.getPapiSupport().hasPlaceholders(line)) {
                            text.setHasPlaceholders(true);
                            break;
                        }
                    }
                } else if (SidebarManager.getPapiSupport().hasPlaceholders(text.getLine())) {
                    text.setHasPlaceholders(true);
                }
            }

            this.text = text;
            setContent(text.getLine());
            sendUpdate();
        }

        private void sendCreate(@NotNull PlayerConnection playerConnection) {
            PacketPlayOutScoreboardTeam packetPlayOutScoreboardTeam = new PacketPlayOutScoreboardTeam(team, 0);
            playerConnection.sendPacket(packetPlayOutScoreboardTeam);
            PacketPlayOutScoreboardScore packetPlayOutScoreboardScore = new PacketPlayOutScoreboardScore(ScoreboardServer.Action.CHANGE, sidebarObjective.getName(), getPlayerName(), getScore());
            playerConnection.sendPacket(packetPlayOutScoreboardScore);
        }

        private void sendCreate() {
            PacketPlayOutScoreboardTeam packetPlayOutScoreboardTeam = new PacketPlayOutScoreboardTeam(team, 0);
            players.forEach(c -> c.sendPacket(packetPlayOutScoreboardTeam));
            PacketPlayOutScoreboardScore packetPlayOutScoreboardScore = new PacketPlayOutScoreboardScore(ScoreboardServer.Action.CHANGE, sidebarObjective.getName(), getPlayerName(), getScore());
            players.forEach(c -> c.sendPacket(packetPlayOutScoreboardScore));
        }

        private void sendRemove(PlayerConnection player) {
            PacketPlayOutScoreboardTeam packetPlayOutScoreboardTeam = new PacketPlayOutScoreboardTeam(team, 1);
            PacketPlayOutScoreboardScore packetPlayOutScoreboardScore = new PacketPlayOutScoreboardScore(ScoreboardServer.Action.REMOVE, sidebarObjective.getName(), getPlayerName(), getScore());
            player.sendPacket(packetPlayOutScoreboardTeam);
            player.sendPacket(packetPlayOutScoreboardScore);
        }

        private void remove() {
            PacketPlayOutScoreboardTeam packetPlayOutScoreboardTeam = new PacketPlayOutScoreboardTeam(team, 1);
            players.forEach(c -> c.sendPacket(packetPlayOutScoreboardTeam));
            PacketPlayOutScoreboardScore packetPlayOutScoreboardScore = new PacketPlayOutScoreboardScore(ScoreboardServer.Action.REMOVE, sidebarObjective.getName(), getPlayerName(), getScore());
            players.forEach(c -> c.sendPacket(packetPlayOutScoreboardScore));
            availableColors.add(getColor());
            this.text = null;
            this.team = null;
            this.prefix = null;
            this.suffix = null;
        }

        @Contract(pure = true)
        private void setContent(@NotNull String content) {
            if (!players.isEmpty()) {
                content = SidebarManager.getPapiSupport().replacePlaceholders(players.get(0).getPlayer(), content);
            }
            if (content.length() > 16) {
                this.prefix = content.substring(0, 16);
                if (this.prefix.charAt(15) == ChatColor.COLOR_CHAR) {
                    this.prefix = content.substring(0, 15);
                    setSuffix(content.substring(15));
                } else {
                    setSuffix(content.substring(16));
                }
            } else {
                this.prefix = content;
                this.suffix = "";
            }
        }

        public void setSuffix(@NotNull String secondPart) {
            if (secondPart.isEmpty()) {
                this.suffix = "";
                return;
            }
            secondPart = ChatColor.getLastColors(this.prefix) + secondPart;
            this.suffix = secondPart.length() > 16 ? secondPart.substring(0, 16) : secondPart;
        }

        private void sendUpdate() {
            PacketPlayOutScoreboardTeam packetPlayOutScoreboardTeam = new PacketPlayOutScoreboardTeam(team, 2);
            players.forEach(c -> c.sendPacket(packetPlayOutScoreboardTeam));
        }

        public int compareTo(@NotNull ScoreLine o) {
            return Integer.compare(score, o.score);
        }

        @Override
        public void setScore(int score) {
            this.score = score;
            PacketPlayOutScoreboardScore packetPlayOutScoreboardScore = new PacketPlayOutScoreboardScore(ScoreboardServer.Action.CHANGE, sidebarObjective.getName(), getPlayerName(), score);
            players.forEach(c -> c.sendPacket(packetPlayOutScoreboardScore));
        }

        @Override
        public int getScore() {
            return score;
        }

        public void c() {
        }

        @Override
        public void addScore(int i) {
        }

        @Override
        public void incrementScore() {
        }

        public String getColor() {
            return team.getName().charAt(0) == ChatColor.COLOR_CHAR ? team.getName() : ChatColor.COLOR_CHAR + team.getName();
        }

        private class TeamLine extends ScoreboardTeam {

            public TeamLine(String color) {
                super(null, color);
                getPlayerNameSet().add(color);
            }

            @Override
            public void setPrefix(@Nullable IChatBaseComponent var0) {
                super.setPrefix(var0);
            }

            public IChatBaseComponent getPrefix() {
                return new ChatComponentText(prefix);
            }

            @Override
            public void setSuffix(@Nullable IChatBaseComponent var0) {
            }

            @Override
            public IChatBaseComponent getSuffix() {
                return new ChatComponentText(suffix);
            }

            @Override
            public void setAllowFriendlyFire(boolean b) {
            }

            @Override
            public void setCanSeeFriendlyInvisibles(boolean b) {
            }

            @Override
            public void setNameTagVisibility(EnumNameTagVisibility enumNameTagVisibility) {
            }

            @Override
            public IChatBaseComponent d() {
                return null;
            }

            @Override
            public void setCollisionRule(EnumTeamPush var0) {
            }

            @Override
            public void setColor(EnumChatFormat var0) {
            }

            @Override
            public IChatBaseComponent getFormattedName(IChatBaseComponent var0) {
                return new ChatComponentText(prefix).addSibling(var0).addSibling(new ChatComponentText(suffix));
            }
        }
    }
}