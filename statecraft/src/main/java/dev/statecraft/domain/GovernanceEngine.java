package dev.statecraft.domain;

import dev.statecraft.api.Actor;
import dev.statecraft.api.ChunkKey;
import dev.statecraft.api.CommandLine;
import dev.statecraft.api.EconomyAccess;
import dev.statecraft.api.GovernanceAccess;
import dev.statecraft.api.Money;
import dev.statecraft.api.UserError;
import dev.statecraft.domain.GovernanceData.Claim;
import dev.statecraft.domain.GovernanceData.Company;
import dev.statecraft.domain.GovernanceData.Government;
import dev.statecraft.domain.GovernanceData.History;
import dev.statecraft.domain.GovernanceData.Invitation;
import dev.statecraft.domain.GovernanceData.Player;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

/**
 * Server-authoritative, Minecraft-independent governance. Public entry points are serialized;
 * the supplied persistence object is always mutated in place.
 */
public class GovernanceEngine implements GovernanceAccess {
    final GovernanceData data;
    GovernanceConfig config;
    EconomyAccess economy;
    final LongSupplier clock;
    final Politics politics;
    final Commerce commerce;
    final Communications communications;
    final Integrity integrity;
    final Territory territory;
    private final Runnable dirty;
    private final Set<String> observedOperators = new HashSet<>();
    private List<String> validationProblems = List.of();

    public GovernanceEngine(GovernanceData data, GovernanceConfig config, EconomyAccess economy,
                            LongSupplier clock) {
        this(data, config, economy, clock, () -> { });
    }

    public GovernanceEngine(GovernanceData data, GovernanceConfig config, EconomyAccess economy,
                            LongSupplier clock, Runnable dirty) {
        this.data = Objects.requireNonNull(data, "data");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.dirty = Objects.requireNonNull(dirty, "dirty");
        configure(config);
        if (Integrity.initialize(data)) changed();
        setEconomy(economy);
        politics = new Politics(this);
        commerce = new Commerce(this);
        communications = new Communications(this);
        territory = new Territory(this);
        integrity = new Integrity(this);
        refreshValidation();
    }

    void changed() {
        dirty.run();
    }

    public synchronized void configure(GovernanceConfig config) {
        Objects.requireNonNull(config, "config").validate();
        this.config = config.copy();
    }

    public synchronized void setEconomy(EconomyAccess economy) {
        this.economy = economy == null ? EconomyAccess.UNAVAILABLE : economy;
        rememberEconomy();
    }

    private void rememberEconomy() {
        if (economy.available() && !data.economySeen) {
            data.economySeen = true;
            changed();
        }
    }

    public synchronized void login(Actor actor) {
        Objects.requireNonNull(actor, "actor");
        String id = actor.id().toString();
        String name = text(actor.name(), 64, "Player name");
        new ChunkKey(actor.dimension(), actor.chunkX(), actor.chunkZ());
        Player player = data.players.get(id);
        if (player == null) {
            player = new Player();
            player.id = id;
            data.players.put(id, player);
            changed();
        }
        if (!id.equals(player.id)) {
            player.id = id;
            changed();
        }
        for (Player other : data.players.values()) {
            if (other != null && other != player && name.equalsIgnoreCase(other.name)
                    && !Objects.equals(other.name, other.id)) {
                other.name = other.id;
                changed();
            }
        }
        if (!name.equals(player.name)) {
            player.name = name;
            changed();
        }
        player.lastSeen = now();
        observeOperator(actor);
    }

    public synchronized String execute(Actor actor, String line) {
        List<String> words = CommandLine.split(line);
        login(actor);
        if (!words.isEmpty() && Set.of("sc", "statecraft", "/sc", "/statecraft").contains(words.get(0)))
            words = words.subList(1, words.size());
        boolean inspectingIntegrity = words.size() >= 2 && "admin".equals(words.get(0))
                && Set.of("audit", "repair", "diagnostics").contains(words.get(1));
        if (!inspectingIntegrity) tick(now());
        if (words.isEmpty()) return bounded(summary());
        Arguments args = new Arguments(words);
        if (!actor.admin() && !Set.of("help", "info").contains(args.get(0).toLowerCase(Locale.ROOT)))
            requireOperational();
        String result = switch (args.get(0).toLowerCase(Locale.ROOT)) {
            case "help" -> {
                args.between(1, 3, "help [section] [page]");
                yield page("StateCraft help", GovernanceHelp.lines(args.optional(1, "overview")), args.page(2));
            }
            case "info" -> {
                args.exactly(1, "info");
                yield summary();
            }
            case "nation" -> governmentCommand(actor, Kind.NATION, args);
            case "state" -> governmentCommand(actor, Kind.STATE, args);
            case "city" -> governmentCommand(actor, Kind.CITY, args);
            case "government" -> governmentCommand(actor, null, args);
            case "chunk" -> chunkCommand(actor, args);
            case "claim" -> chunkCommand(actor, prefixed("chunk", "claim", words.subList(1, words.size())));
            case "unclaim" -> chunkCommand(actor, prefixed("chunk", "unclaim", words.subList(1, words.size())));
            case "map" -> chunkCommand(actor, prefixed("chunk", "map", words.subList(1, words.size())));
            case "election" -> politics.electionCommand(actor, args);
            case "bill" -> politics.billCommand(actor, args);
            case "law" -> politics.lawCommand(actor, args);
            case "executive" -> politics.executiveCommand(actor, args);
            case "diplomacy" -> politics.diplomacyCommand(actor, args);
            case "company" -> commerce.companyCommand(actor, args);
            case "contract" -> commerce.contractCommand(actor, args);
            case "mail" -> communications.command(actor, args);
            case "profile" -> communications.profile(actor, args);
            case "admin" -> adminCommand(actor, args);
            default -> throw new UserError("Unknown command '" + args.get(0) + "'. Use help.");
        };
        if (actor.admin() && (!validationProblems.isEmpty() || inspectingIntegrity)) refreshValidation();
        return bounded(result);
    }

    public synchronized void tick(long now) {
        check(now >= 0, "Time cannot be negative.");
        if (!validationProblems.isEmpty()) return;
        if (now < data.lastTick) return;
        data.lastTick = now;
        rememberEconomy();
        if (data.invitations.values().removeIf(invite -> invite != null && invite.expiresAt <= now)) changed();
        politics.tick(now);
        commerce.tick(now);
        integrity.boundRetention();
    }

    public synchronized List<String> validationIssues() {
        if (Integrity.initialize(data)) changed();
        refreshValidation();
        return validationProblems;
    }

    private void refreshValidation() {
        validationProblems = List.copyOf(integrity.startupIssues());
    }

    private void requireOperational() {
        check(validationProblems.isEmpty(), "Governance data requires operator repair (" + validationProblems.size()
                + " issue(s)). No records were discarded. Use admin audit, then explicit repair/reassignment.");
    }

    @Override
    public synchronized Collection<GovernmentView> governments() {
        return data.governments.values().stream().filter(this::viewableGovernment)
                .sorted(Comparator.comparing(g -> g.id)).map(this::view).toList();
    }

    @Override
    public synchronized Optional<GovernmentView> government(String idOrName) {
        return findGovernment(idOrName).filter(this::viewableGovernment).map(this::view);
    }

    @Override
    public synchronized Collection<CompanyView> companies() {
        return data.companies.values().stream().filter(this::viewableCompany)
                .sorted(Comparator.comparing(c -> c.id)).map(this::view).toList();
    }

    @Override
    public synchronized Optional<CompanyView> company(String idOrName) {
        return findCompany(idOrName).filter(this::viewableCompany).map(this::view);
    }

    @Override
    public synchronized Collection<ClaimView> claims() {
        return data.claims.values().stream().filter(this::viewableClaim)
                .sorted(Comparator.comparing(c -> c.key)).map(this::view).toList();
    }

    @Override
    public synchronized Optional<ClaimView> claim(String key) {
        Claim claim = data.claims.get(key);
        return viewableClaim(claim) ? Optional.of(view(claim)) : Optional.empty();
    }

    @Override
    public synchronized Optional<String> nationOf(UUID player) {
        Player known = player == null ? null : data.players.get(player.toString());
        if (known == null) return Optional.empty();
        Government nation = data.governments.get(known.nationId);
        return validHierarchy(nation) && nation.kind == Kind.NATION
                ? Optional.of(nation.id) : Optional.empty();
    }

    @Override
    public synchronized boolean mayManageGovernment(UUID player, String governmentId) {
        Government government = data.governments.get(governmentId);
        if (player == null || !validHierarchy(government)) return false;
        String id = player.toString();
        for (Government scope : ancestors(government)) {
            if (member(id, scope) && (id.equals(scope.leader) || scope.officers.contains(id))) return true;
        }
        return false;
    }

    @Override
    public synchronized boolean mayManageCompany(UUID player, String companyId) {
        Company company = data.companies.get(companyId);
        return player != null && viewableCompany(company) && data.players.containsKey(player.toString())
                && company.members.contains(player.toString())
                && (player.toString().equals(company.owner) || company.officers.contains(player.toString()));
    }

    @Override
    public synchronized boolean mayAccessAccount(UUID player, String account) {
        if (player == null || !data.players.containsKey(player.toString()) || account == null) return false;
        if (account.equals("player:" + player)) return true;
        String[] parts = account.split(":", 2);
        if (parts.length != 2) return false;
        if ("company".equals(parts[0])) return mayManageCompany(player, parts[1]);
        Government government = data.governments.get(parts[1]);
        return government != null && government.kind != null && account.equals(account(government))
                && mayManageGovernment(player, government.id);
    }

    @Override
    public synchronized Set<String> accountsFor(UUID player) {
        Set<String> result = new LinkedHashSet<>();
        if (player == null || !data.players.containsKey(player.toString())) return Set.of();
        result.add("player:" + player);
        for (Government government : data.governments.values()) {
            if (government != null && government.kind != null && mayAccessAccount(player, account(government)))
                result.add(account(government));
        }
        for (Company company : data.companies.values()) {
            if (company != null && mayAccessAccount(player, "company:" + company.id))
                result.add("company:" + company.id);
        }
        return Set.copyOf(result);
    }

    @Override
    public synchronized boolean maySellProperty(UUID player, String chunkKey) {
        if (!validationProblems.isEmpty()) return false;
        Claim claim = data.claims.get(chunkKey);
        return viewableClaim(claim) && mayAccessAccount(player, claim.ownerAccount)
                && !commerce.claimLocked(chunkKey) && !politics.claimLocked(chunkKey, null);
    }

    @Override
    public synchronized boolean mayBuyProperty(UUID player, String chunkKey) {
        if (!validationProblems.isEmpty()) return false;
        Claim claim = data.claims.get(chunkKey);
        if (player == null || !data.players.containsKey(player.toString()) || !viewableClaim(claim)) return false;
        return flag(claimGovernment(claim), "foreignProperty") || nationOf(player).filter(claim.nationId::equals).isPresent();
    }

    @Override
    public synchronized void transferProperty(String chunkKey, String ownerAccount) {
        requireOperational();
        changeProperty(chunkKey, ownerAccount);
    }

    private void changeProperty(String chunkKey, String ownerAccount) {
        Claim claim = requiredClaim(chunkKey);
        check(viewableClaim(claim), "Repair this claim's hierarchy/key before transferring its private title.");
        validateOwnerAccount(ownerAccount);
        // Company/government settlement destinations include secured-loan repossession, not just purchases.
        check(!ownerAccount.startsWith("player:") || eligibleOwner(claim, ownerAccount),
                "Foreign property ownership is not enabled here.");
        assertClaimFree(claim.key, null);
        if (ownerAccount.equals(claim.ownerAccount)) return;
        String previous = claim.ownerAccount;
        changed();
        claim.ownerAccount = ownerAccount;
        claim.permits.clear();
        history("property", chunkKey, previous + " -> " + ownerAccount);
    }

    @Override
    public synchronized long sharesOf(String companyId, UUID shareholder) {
        return commerce.sharesOf(companyId, shareholder);
    }

    @Override
    public synchronized long availableShares(String companyId, UUID shareholder) {
        return commerce.availableShares(companyId, shareholder);
    }

    @Override
    public synchronized void transferShares(String companyId, UUID from, UUID to, long quantity) {
        requireOperational();
        commerce.transferShares(companyId, from, to, quantity);
    }

    @Override
    public synchronized void reserveShares(String companyId, UUID owner, String reference, long quantity) {
        GovernanceData.ShareReservation existing = data.shareReservations.get(reference);
        if (existing == null || owner == null || !Objects.equals(existing.companyId, companyId)
                || !Objects.equals(existing.owner, owner.toString()) || existing.quantity != quantity)
            requireOperational();
        commerce.reserveShares(companyId, owner, reference, quantity);
    }

    @Override
    public synchronized void releaseShares(String reference) {
        commerce.releaseShares(reference);
        if (!validationProblems.isEmpty()) refreshValidation();
    }

    @Override
    public synchronized void settleShares(String reference, UUID buyer, long quantity) {
        requireOperational();
        commerce.settleShares(reference, buyer, quantity);
    }

    @Override
    public synchronized void mail(UUID recipient, String subject, String body) {
        requirePlayer(recipient);
        communications.deliver("system", "player:" + recipient, subject, body, false);
    }

    @Override
    public synchronized void governmentMail(String governmentId, String subject, String body) {
        Government government = gov(governmentId);
        communications.deliver("system", account(government), subject, body, false);
    }

    public synchronized boolean mayAct(Actor actor, String chunkKey, AccessAction action, UUID targetPlayer) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(action, "action");
        observeOperator(actor);
        return mayActChecked(actor, chunkKey, action, targetPlayer);
    }

    private boolean mayActChecked(Actor actor, String chunkKey, AccessAction action, UUID targetPlayer) {
        if (!canonicalChunk(chunkKey)) return false;
        if (actor.admin() && bypassEnabled(actor.id())) return true;
        Claim claim = data.claims.get(chunkKey);
        if (data.claims.containsKey(chunkKey) && (claim == null || !validationProblems.isEmpty())) return false;
        boolean pvp = action == AccessAction.PVP || (action == AccessAction.ATTACK && targetPlayer != null);
        if (claim == null) return !pvp || pvpAllowed(actor.id(), targetPlayer, null);
        if (!viewableClaim(claim)) return false;
        Government jurisdiction = claimGovernment(claim);
        if (pvp) return pvpAllowed(actor.id(), targetPlayer, jurisdiction);
        if (mayAccessAccount(actor.id(), claim.ownerAccount)) return true;
        Set<String> permits = claim.permits.get(actor.id().toString());
        if (permits != null && permits.contains(action.name())) return true;
        // Diplomatic public access must never silently open privately purchased buildings.
        if (!publicTitle(claim)) return false;
        String visitorNation = nationOf(actor.id()).orElse(null);
        String landNation = claim.nationId;
        if (landNation.equals(visitorNation)) return flag(jurisdiction, "memberAccess");
        if (visitorNation == null) return flag(jurisdiction, "foreignAccess");
        return switch (politics.relationStatus(visitorNation, landNation)) {
            case "ALLIED" -> flag(jurisdiction, "alliedAccess");
            case "WAR" -> flag(jurisdiction, "enemyAccess");
            default -> flag(jurisdiction, "foreignAccess");
        };
    }

    public synchronized boolean allowsExplosion(String chunkKey) {
        if (!canonicalChunk(chunkKey)) return false;
        Claim claim = data.claims.get(chunkKey);
        return claim == null ? !data.claims.containsKey(chunkKey) && config.wildernessExplosions
                : validationProblems.isEmpty() && viewableClaim(claim) && flag(claimGovernment(claim), "explosions");
    }

    public synchronized void recordImprovement(String chunkKey, int delta) {
        Claim claim = data.claims.get(chunkKey);
        if (claim == null) return;
        int improvements = (int) Math.max(0, Math.min(1_000_000_000L, (long) claim.improvements + delta));
        if (claim.improvements != improvements) {
            claim.improvements = improvements;
            changed();
        }
    }

    public synchronized boolean autoClaimEnabled(UUID player) {
        Player known = player == null ? null : data.players.get(player.toString());
        return known != null && known.autoClaim;
    }

    public synchronized void autoClaim(Actor actor) {
        login(actor);
        if (!autoClaimEnabled(actor.id())) return;
        requireOperational();
        Government nation = ownGovernment(actor, Kind.NATION);
        territory.nationalManager(actor, nation);
        Claim existing = data.claims.get(actor.chunkKey());
        if (viewableClaim(existing) && nation.id.equals(existing.nationId)) return;
        claimChunk(actor, nation, actor.chunkKey());
    }

    /** Informational only: protection also checks the current Actor.admin on every action. */
    public synchronized boolean bypassEnabled(UUID player) {
        Player known = player == null ? null : data.players.get(player.toString());
        return known != null && known.bypass && observedOperators.contains(player.toString());
    }

    private boolean pvpAllowed(UUID attacker, UUID target, Government city) {
        if (target == null || attacker.equals(target)) return false;
        String first = nationOf(attacker).orElse(null);
        String second = nationOf(target).orElse(null);
        boolean related = first != null && second != null;
        boolean friendlyFire = city != null && flag(city, "friendlyFire");
        if (related && config.protectFriendlyPvp && !friendlyFire) {
            if (first.equals(second) || "ALLIED".equals(politics.relationStatus(first, second))
                    || politics.truce(first, second, now())) return false;
        }
        boolean permitted = city == null ? config.wildernessPvp : flag(city, "pvp");
        if (!permitted && city != null && related && config.warOverridesPvp
                && "WAR".equals(politics.relationStatus(first, second))) {
            String ownerNation = nationId(city);
            permitted = ownerNation.equals(first) || ownerNation.equals(second);
        }
        return permitted;
    }

    private void observeOperator(Actor actor) {
        if (actor.admin()) observedOperators.add(actor.id().toString());
        else {
            observedOperators.remove(actor.id().toString());
            Player player = data.players.get(actor.id().toString());
            if (player != null && player.bypass) {
                player.bypass = false;
                changed();
            }
        }
    }

    private String governmentCommand(Actor actor, Kind kind, Arguments args) {
        String action = args.optional(1, "list");
        String family = kind == null ? "government" : kind.name().toLowerCase(Locale.ROOT);
        if ("list".equals(action)) {
            args.between(1, 3, family + " list [page]");
            List<String> rows = data.governments.values().stream().filter(this::viewableGovernment)
                    .filter(g -> kind == null || g.kind == kind).sorted(Comparator.comparing(g -> g.name))
                    .map(g -> g.kind + " " + g.name + " [" + g.id + "] leader=" + playerName(g.leader)
                            + " citizens=" + members(g).size()).toList();
            return page("Governments", rows, args.page(2));
        }
        if ("create".equals(action)) {
            check(kind != null, "Choose nation create, state create, or city create.");
            return createGovernment(actor, kind, args);
        }
        Government government;
        if (args.size() > 2) government = gov(args.get(2));
        else {
            check(kind != null, "Specify a government name or UUID.");
            government = ownGovernment(actor, kind);
        }
        check(kind == null || government.kind == kind, "That is not a " + family + ".");
        switch (action) {
            case "info" -> {
                args.between(2, 3, family + " info [government]");
                return governmentInfo(government);
            }
            case "members", "roles", "officers" -> {
                args.between(3, 4, family + " " + action + " <government> [page]");
                List<String> rows = members(government).stream().sorted().map(id -> playerName(id) + " [" + id
                        + "] " + role(id, government)).filter(row -> !"officers".equals(action)
                        || !row.endsWith("citizen")).toList();
                return page(government.name + " members", rows, args.page(3));
            }
            case "join", "accept" -> {
                args.exactly(3, family + " " + action + " <government>");
                joinGovernment(actor, government, "accept".equals(action));
                return "Joined " + government.name + ".";
            }
            case "leave" -> {
                args.between(2, 3, family + " leave [government]");
                check(member(actor.id().toString(), government), "You are not a citizen of this government.");
                leaveGovernment(actor.id().toString(), government);
                return "Left " + government.name + "; parent citizenship was preserved.";
            }
            case "invite" -> {
                args.exactly(4, family + " invite <government> <player>");
                manage(actor, government);
                Player target = resolvePlayer(args.get(3));
                inviteGovernment(actor, government, target);
                return "Invited " + target.name + " to " + government.name + ".";
            }
            case "invitations" -> {
                args.between(3, 4, family + " invitations <government> [page]");
                List<String> rows = data.invitations.values().stream().filter(i -> i != null
                                && government.id.equals(i.governmentId)
                                && (canManage(actor, government) || actor.id().toString().equals(i.playerId)))
                        .map(i -> playerName(i.playerId) + " [" + i.id + "] expires=" + i.expiresAt).toList();
                return page("Invitations", rows, args.page(3));
            }
            case "revoke", "decline" -> {
                args.exactly("revoke".equals(action) ? 4 : 3,
                        family + " " + action + " <government>" + ("revoke".equals(action) ? " <player>" : ""));
                String player = actor.id().toString();
                if ("revoke".equals(action)) {
                    manage(actor, government);
                    player = resolvePlayer(args.get(3)).id;
                }
                Invitation invite = invitation(government.id, null, player);
                check(invite != null, "No active invitation exists.");
                data.invitations.remove(invite.id);
                changed();
                return "Invitation removed.";
            }
            case "kick" -> {
                args.exactly(4, family + " kick <government> <player>");
                manage(actor, government);
                Player target = resolvePlayer(args.get(3));
                check(!actor.id().toString().equals(target.id), "Use leave to remove your own membership.");
                check(member(target.id, government), "That player is not a citizen of this government.");
                check(mayRemoveMembership(actor, target.id, government),
                        "Only a superior official may remove an ancestor official's descendant membership.");
                leaveGovernment(target.id, government);
                mail(UUID.fromString(target.id), "Citizenship removed", "You were removed from " + government.name + ".");
                return "Removed " + target.name + " from " + government.name + ".";
            }
            case "leader", "appoint" -> {
                args.exactly(4, family + " leader <government> <player>");
                executive(actor, government);
                appointLeader(government, resolvePlayer(args.get(3)).id);
                return "Leadership transferred to " + playerName(government.leader) + ".";
            }
            case "officer" -> {
                args.exactly(5, family + " officer <government> <player> <add|remove>");
                executive(actor, government);
                Player target = resolvePlayer(args.get(3));
                check(member(target.id, government), "Officers must be citizens of this government.");
                check(!target.id.equals(government.leader), "The leader already has executive permissions.");
                String operation = args.get(4);
                check(Set.of("add", "remove").contains(operation), "Use add or remove.");
                if ("add".equals(operation)) {
                    check(government.officers.size() < config.maxOfficers, "Officer limit reached.");
                    check(government.officers.add(target.id), "That player is already an officer.");
                } else check(government.officers.remove(target.id), "That player is not an officer.");
                history("role", government.id, operation + " officer " + target.id);
                return "Officer roster updated.";
            }
            case "rename", "description", "tag", "flag" -> {
                args.between(4, 64, family + " " + action + " <government> <text>");
                manage(actor, government);
                String value = args.tail(3);
                switch (action) {
                    case "rename" -> renameGovernment(government, value);
                    case "description" -> government.description = prose(value, config.maxDescriptionLength, "Description");
                    case "tag" -> {
                        check(value.matches("[A-Za-z0-9_-]{1,12}"), "Tags use 1-12 letters, numbers, underscores or hyphens.");
                        government.tag = value;
                    }
                    case "flag" -> government.flag = text(value, 128, "Flag description");
                    default -> throw new IllegalStateException();
                }
                history("government", government.id, action + " by " + actor.id());
                return "Updated " + action + " for " + government.name + ".";
            }
            case "settings" -> {
                args.exactly(3, family + " settings <government>");
                return government.name + " effective settings:\n" + settings(government).entrySet().stream()
                        .map(e -> e.getKey() + "=" + e.getValue() + (government.settings.containsKey(e.getKey()) ? " (local)" : " (default/inherited)"))
                        .collect(Collectors.joining("\n"));
            }
            case "setting" -> {
                args.exactly(5, family + " setting <government> <key> <value|inherit>");
                executive(actor, government);
                String key = args.get(3);
                check(!requiresNationalLegislation(actor, government, key),
                        "This server requires legislation for national mechanical policies; local policies remain executive actions.");
                setPolicy(government, key, args.get(4));
                history("policy", government.id, key + "=" + args.get(4) + " by " + actor.id());
                return "Updated policy " + key + ".";
            }
            case "disband" -> {
                args.between(3, 4, family + " disband <government> [cascade]");
                executive(actor, government);
                boolean cascade = args.size() == 4;
                if (cascade) check("cascade".equals(args.get(3)), "Explicitly specify cascade to remove descendants.");
                disband(government, cascade, actor.admin());
                return "Disbanded " + government.name + ".";
            }
            default -> throw new UserError("Unknown " + family + " action '" + action + "'. Use help governments.");
        }
    }

    private String createGovernment(Actor actor, Kind kind, Arguments args) {
        args.between(kind == Kind.NATION ? 3 : 4, kind == Kind.NATION ? 4 : 5,
                kind == Kind.NATION ? "nation create <name> [leader: admin only]"
                        : kind.name().toLowerCase(Locale.ROOT) + " create <parent> <name> [leader]");
        Government parent = kind == Kind.NATION ? null : gov(args.get(2));
        if (parent != null) {
            check(parent.kind == (kind == Kind.STATE ? Kind.NATION : Kind.STATE), "Invalid parent government level.");
            manage(actor, parent);
        }
        String name = validGovernmentName(args.get(kind == Kind.NATION ? 2 : 3), null);
        int leaderIndex = kind == Kind.NATION ? 3 : 4;
        Player leader = args.size() > leaderIndex ? resolvePlayer(args.get(leaderIndex)) : requirePlayer(actor.id());
        if (kind == Kind.NATION && !leader.id.equals(actor.id().toString())) admin(actor);
        checkCreationCapacity(kind, parent);
        checkCreationLeader(kind, parent, leader);
        Government government = new Government();
        government.id = newId();
        government.kind = kind;
        government.name = name;
        government.parentId = parent == null ? null : parent.id;
        government.leader = leader.id;
        government.createdAt = now();
        pay(List.of(creationFee(actor, kind, parent)));
        data.governments.put(government.id, government);
        setMembership(leader, government);
        if (kind == Kind.NATION) politics.schedule(government);
        history("government", government.id, "Created " + kind + " " + name + " by " + actor.id());
        return "Created " + kind.name().toLowerCase(Locale.ROOT) + " " + name + " [" + government.id + "].";
    }

    EconomyAccess.Transfer creationFee(Actor actor, Kind kind, Government parent) {
        long fee = switch (kind) {
            case NATION -> config.nationCreationFee;
            case STATE -> config.stateCreationFee;
            case CITY -> config.cityCreationFee;
        };
        return new EconomyAccess.Transfer(actor.account(), parent == null ? config.feeAccount : account(parent),
                fee, "Create " + kind.name().toLowerCase(Locale.ROOT));
    }

    void checkCreationCapacity(Kind kind, Government parent) {
        check(data.governments.size() < config.maxGovernments, "World government limit reached.");
        long existing = data.governments.values().stream().filter(Objects::nonNull).filter(g -> g.kind == kind
                && (parent == null || parent.id.equals(g.parentId))).count();
        int limit = switch (kind) {
            case NATION -> config.maxNations;
            case STATE -> config.maxStatesPerNation;
            case CITY -> config.maxCitiesPerState;
        };
        check(existing < limit, "The " + kind.name().toLowerCase(Locale.ROOT) + " limit has been reached.");
    }

    void checkCreationLeader(Kind kind, Government parent, Player leader) {
        if (kind == Kind.NATION) check(leader.nationId == null, "The founder must first leave their existing nation.");
        if (kind == Kind.STATE) {
            check(member(leader.id, parent), "A governor must already be a citizen of the parent nation.");
            check(leader.stateId == null, "The governor must first leave their current state.");
        }
        if (kind == Kind.CITY) {
            check(member(leader.id, parent), "A mayor must already be a citizen of the parent state.");
            check(leader.cityId == null, "The mayor must first leave their current city.");
        }
    }

    boolean requiresRepair() {
        return !validationProblems.isEmpty();
    }

    void joinGovernment(Actor actor, Government government, boolean invitationOnly) {
        Player player = requirePlayer(actor.id());
        check(!member(player.id, government), "You are already a citizen of this government.");
        checkJoinPath(player, government);
        check(members(government).size() < memberLimit(government.kind), "Citizenship limit reached.");
        Invitation invitation = invitation(government.id, null, player.id);
        check(invitation != null || (!invitationOnly && flag(government, "open")),
                "Membership is invitation-only; ask an official for an invitation.");
        setMembership(player, government);
        if (invitation != null) data.invitations.remove(invitation.id);
        history("membership", government.id, "Joined " + player.id);
    }

    void checkJoinPath(Player player, Government government) {
        switch (government.kind) {
            case NATION -> check(player.nationId == null, "Leave your current nation before joining another.");
            case STATE -> {
                check(Objects.equals(player.nationId, government.parentId), "Join the parent nation first.");
                check(player.stateId == null, "Leave your current state before joining another.");
            }
            case CITY -> {
                check(Objects.equals(player.stateId, government.parentId), "Join the parent state first.");
                check(player.cityId == null, "Leave your current city before joining another.");
            }
        }
    }

    void setMembership(Player player, Government government) {
        changed();
        switch (government.kind) {
            case NATION -> {
                if (!government.id.equals(player.nationId)) {
                    player.stateId = null;
                    player.cityId = null;
                    player.autoClaim = false;
                }
                player.nationId = government.id;
            }
            case STATE -> {
                if (!government.id.equals(player.stateId)) {
                    player.cityId = null;
                }
                if (!government.parentId.equals(player.nationId)) player.autoClaim = false;
                player.nationId = government.parentId;
                player.stateId = government.id;
            }
            case CITY -> {
                if (!nationId(government).equals(player.nationId)) player.autoClaim = false;
                player.nationId = nationId(government);
                player.stateId = government.parentId;
                player.cityId = government.id;
            }
        }
    }

    Set<String> membershipRemovalScope(String playerId, Government government) {
        Set<String> scope = subtree(government).stream().map(g -> g.id).collect(Collectors.toSet());
        for (Government child : data.governments.values()) {
            if (child != null && scope.contains(child.id))
                check(!playerId.equals(child.leader), "Transfer leadership of " + child.name + " before leaving or expelling its leader.");
        }
        return scope;
    }

    void leaveGovernment(String playerId, Government government) {
        Set<String> scope = membershipRemovalScope(playerId, government);
        Player player = data.players.get(playerId);
        changed();
        clearMembership(player, scope);
        for (Government child : data.governments.values())
            if (child != null && scope.contains(child.id)) child.officers.remove(playerId);
        data.invitations.values().removeIf(i -> i != null && playerId.equals(i.playerId)
                && i.governmentId != null && !eligibleInvitation(player, data.governments.get(i.governmentId)));
        politics.membershipChanged(playerId);
        history("membership", government.id, "Left " + playerId);
    }

    private void inviteGovernment(Actor actor, Government government, Player target) {
        check(!member(target.id, government), "That player is already a citizen.");
        check(eligibleInvitation(target, government), "The invitee must have matching parent citizenship and no conflicting membership.");
        Invitation existing = invitation(government.id, null, target.id);
        if (existing == null) {
            checkInvitationCapacity(government.id, null, target.id);
            existing = new Invitation();
            existing.id = newId();
            existing.governmentId = government.id;
            existing.playerId = target.id;
        }
        changed();
        existing.invitedBy = actor.id().toString();
        existing.expiresAt = deadline(now(), config.invitationDurationMillis);
        data.invitations.put(existing.id, existing);
        mail(UUID.fromString(target.id), "Government invitation",
                "You are invited to " + government.name + ". Use " + government.kind.name().toLowerCase(Locale.ROOT)
                        + " accept " + CommandLine.quote(government.id) + ". Expires " + existing.expiresAt + ".");
    }

    boolean eligibleInvitation(Player player, Government government) {
        if (player == null || !validHierarchy(government)) return false;
        return switch (government.kind) {
            case NATION -> player.nationId == null;
            case STATE -> Objects.equals(player.nationId, government.parentId) && player.stateId == null;
            case CITY -> Objects.equals(player.stateId, government.parentId) && player.cityId == null;
        };
    }

    Invitation invitation(String governmentId, String companyId, String player) {
        return data.invitations.values().stream().filter(Objects::nonNull)
                .filter(i -> validUuid(i.id) && data.invitations.get(i.id) == i)
                .filter(i -> Objects.equals(governmentId, i.governmentId) && Objects.equals(companyId, i.companyId)
                        && player.equals(i.playerId) && i.expiresAt > now()).findFirst().orElse(null);
    }

    void checkInvitationCapacity(String governmentId, String companyId, String player) {
        check(data.invitations.values().stream().filter(Objects::nonNull).filter(i -> i.expiresAt > now()
                        && Objects.equals(governmentId, i.governmentId) && Objects.equals(companyId, i.companyId)).count()
                        < config.maxInvitationsPerGovernment, "This organization has too many outstanding invitations.");
        check(data.invitations.values().stream().filter(Objects::nonNull)
                .filter(i -> i.expiresAt > now() && player.equals(i.playerId)).count()
                < config.maxInvitationsPerPlayer, "That player has too many outstanding invitations.");
    }

    void appointLeader(Government government, String playerId) {
        check(member(playerId, government), "The new leader must be a citizen of this government.");
        check(!playerId.equals(government.leader), "That player is already leader.");
        String previous = government.leader;
        changed();
        government.leader = playerId;
        government.officers.remove(playerId);
        history("leadership", government.id, previous + " -> " + playerId);
        governmentMail(government.id, "Leadership transfer", playerName(playerId) + " is now leader of " + government.name + ".");
    }

    void renameGovernment(Government government, String name) {
        government.name = validGovernmentName(name, government.id);
        changed();
    }

    List<Government> disbandPlan(Government government, boolean cascade, boolean administrator) {
        List<Government> removed = subtree(government);
        Set<String> ids = removed.stream().map(g -> g.id).collect(Collectors.toSet());
        List<Claim> claims = data.claims.values().stream().filter(Objects::nonNull)
                .filter(c -> ids.contains(c.nationId) || ids.contains(c.stateId) || ids.contains(c.cityId)).toList();
        check(government.kind == Kind.NATION || claims.isEmpty(),
                "Clear this government's state/city allocations before disbanding it. Nation land is never silently unclaimed.");
        check(cascade || (removed.size() == 1 && claims.isEmpty() && members(government).size() <= 1),
                "Government is populated or has children/claims; explicitly use cascade.");
        for (Government current : removed) {
            check(validHierarchy(current), "Repair invalid child hierarchies before disbanding this government.");
            assertAccountDisposable(account(current));
            check(!commerce.governmentLocked(current.id) && !politics.governmentLocked(current.id),
                    "Resolve active elections, bills, contracts or diplomacy before disbanding " + current.name + ".");
        }
        Set<String> accounts = removed.stream().map(this::account).collect(Collectors.toSet());
        for (Claim claim : claims) {
            check(viewableClaim(claim) && government.id.equals(claim.nationId),
                    "Repair inconsistent claims before disbanding their nation.");
            assertClaimFree(claim.key, null);
            check(publicTitle(claim) && accounts.contains(claim.ownerAccount),
                    "Private or externally owned property must be returned to its nation's public ownership before disbanding.");
        }
        check(data.claims.values().stream().filter(Objects::nonNull)
                        .noneMatch(c -> !claims.contains(c) && accounts.contains(c.ownerAccount)),
                "A treasury owns property outside this subtree; transfer it first.");
        return removed;
    }

    void disband(Government government, boolean cascade, boolean administrator) {
        List<Government> removed = disbandPlan(government, cascade, administrator);
        Set<String> ids = removed.stream().map(g -> g.id).collect(Collectors.toSet());
        changed();
        for (Player player : data.players.values()) if (player != null) clearMembership(player, ids);
        for (String id : ids) {
            data.governments.remove(id);
            data.elections.remove(id);
            data.laws.remove(id);
            data.emergencies.remove(id);
        }
        if (government.kind == Kind.NATION)
            data.claims.values().removeIf(c -> c != null && government.id.equals(c.nationId));
        data.bills.values().removeIf(b -> b != null && ids.contains(b.nationId));
        data.invitations.values().removeIf(i -> i != null && ids.contains(i.governmentId));
        history("government", government.id, "Disbanded " + government.name + (cascade ? " with descendants" : ""));
    }

    void clearMembership(Player player, Set<String> removed) {
        if (player == null) return;
        if (removed.contains(player.nationId)) {
            changed();
            player.nationId = null;
            player.stateId = null;
            player.cityId = null;
        } else if (removed.contains(player.stateId)) {
            changed();
            player.stateId = null;
            player.cityId = null;
        } else if (removed.contains(player.cityId)) {
            player.cityId = null;
            changed();
        }
        if (player.nationId == null && player.autoClaim) {
            player.autoClaim = false;
            changed();
        }
    }

    private String chunkCommand(Actor actor, Arguments args) {
        String action = args.optional(1, "info");
        switch (action) {
            case "claim" -> {
                args.between(2, 4, "chunk claim [nation] [chunks|here]");
                boolean locationOnly = args.size() == 3 && ("here".equals(args.get(2)) || args.get(2).contains("|"));
                Government nation = args.size() > 2 && !locationOnly ? gov(args.get(2)) : ownGovernment(actor, Kind.NATION);
                List<String> keys = territory.keys(actor, locationOnly ? args.get(2) : args.optional(3, "here"));
                territory.claim(actor, nation, keys);
                return "Claimed " + keys.size() + " chunk(s) for " + nation.name + "; state and city are unassigned.";
            }
            case "unclaim" -> {
                args.between(2, 4, "chunk unclaim [nation] [chunks|here]");
                boolean locationOnly = args.size() == 3 && ("here".equals(args.get(2)) || args.get(2).contains("|"));
                Government nation = args.size() > 2 && !locationOnly ? gov(args.get(2)) : ownGovernment(actor, Kind.NATION);
                List<String> keys = territory.keys(actor, locationOnly ? args.get(2) : args.optional(3, "here"));
                territory.unclaim(actor, nation, keys, false);
                return "Unclaimed " + keys.size() + " chunk(s) from " + nation.name + ".";
            }
            case "assignstate" -> {
                args.exactly(5, "chunk assignstate <nation> <chunks> <state_or_none>");
                Government nation = gov(args.get(2));
                Government target = "none".equals(args.get(4)) ? null : gov(args.get(4));
                List<Territory.Allocation> plans = territory.statePlan(actor, nation, territory.keys(actor, args.get(3)), target);
                territory.apply(plans, "allocation", actor);
                return "Updated state allocation for " + plans.size() + " chunk(s); private titles and treasury balances are unchanged.";
            }
            case "assigncity" -> {
                args.exactly(5, "chunk assigncity <state> <chunks> <city_or_none>");
                Government state = gov(args.get(2));
                Government target = "none".equals(args.get(4)) ? null : gov(args.get(4));
                List<Territory.Allocation> plans = territory.cityPlan(actor, state, territory.keys(actor, args.get(3)), target);
                territory.apply(plans, "allocation", actor);
                return "Updated city allocation for " + plans.size() + " chunk(s); private titles and treasury balances are unchanged.";
            }
            case "autoclaim" -> {
                args.exactly(3, "chunk autoclaim <on|off>");
                check(Set.of("on", "off").contains(args.get(2)), "Use on or off.");
                boolean enabled = "on".equals(args.get(2));
                if (enabled) territory.nationalManager(actor, ownGovernment(actor, Kind.NATION));
                Player player = requirePlayer(actor.id());
                if (player.autoClaim != enabled) {
                    player.autoClaim = enabled;
                    changed();
                }
                return "Automatic claiming " + (enabled ? "enabled for your nation." : "disabled.");
            }
            case "info" -> {
                args.between(1, 3, "chunk info [chunkKey|here]");
                return chunkInfo(chunkKey(actor, args.optional(2, "here")));
            }
            case "list" -> {
                args.between(2, 4, "chunk list [government|all] [page]");
                String ref = args.optional(2, "all");
                Government government = "all".equals(ref) ? null : gov(ref);
                List<String> rows = data.claims.values().stream().filter(this::viewableClaim)
                        .filter(c -> government == null || claimInGovernment(c, government)).sorted(Comparator.comparing(c -> c.key))
                        .map(c -> c.key + " nation=" + governmentName(c.nationId)
                                + " state=" + governmentName(c.stateId) + " city=" + governmentName(c.cityId) + " owner=" + c.ownerAccount
                                + " improvements=" + c.improvements).toList();
                return page("Claims", rows, args.page(3));
            }
            case "permit" -> {
                args.exactly(5, "chunk permit <chunkKey|here> <player> <all|none|ACTION,ACTION>");
                Claim claim = requiredClaim(chunkKey(actor, args.get(2)));
                check(actor.admin() || mayAccessAccount(actor.id(), claim.ownerAccount), "Only the private owner or its managers may grant permits.");
                Player target = resolvePlayer(args.get(3));
                Set<String> actions = new LinkedHashSet<>();
                if ("all".equals(args.get(4))) {
                    EnumSet.allOf(AccessAction.class).stream().filter(a -> a != AccessAction.PVP).forEach(a -> actions.add(a.name()));
                } else if (!"none".equals(args.get(4))) {
                    for (String name : args.get(4).split(",", -1)) {
                        try {
                            AccessAction parsed = AccessAction.valueOf(name.toUpperCase(Locale.ROOT));
                            check(parsed != AccessAction.PVP, "Permits cannot override PvP or diplomacy.");
                            actions.add(parsed.name());
                        } catch (IllegalArgumentException e) {
                            throw new UserError("Unknown permit action: " + name + ".");
                        }
                    }
                }
                if (actions.isEmpty()) claim.permits.remove(target.id);
                else {
                    check(claim.permits.containsKey(target.id) || claim.permits.size() < config.maxPermitsPerChunk,
                            "Chunk permit limit reached.");
                    claim.permits.put(target.id, actions);
                }
                history("permit", claim.key, target.id + "=" + String.join(",", actions));
                return "Updated chunk permits for " + target.name + ".";
            }
            case "permits" -> {
                args.between(2, 4, "chunk permits [chunkKey|here] [page]");
                Claim claim = requiredClaim(chunkKey(actor, args.optional(2, "here")));
                check(actor.admin() || mayAccessAccount(actor.id(), claim.ownerAccount), "Only the owner may list permits.");
                return page("Chunk permits", claim.permits.entrySet().stream()
                        .filter(entry -> entry.getValue() != null)
                        .map(entry -> playerName(entry.getKey()) + ": " + String.join(",", entry.getValue())).toList(), args.page(3));
            }
            case "map" -> {
                args.between(2, 3, "chunk map [radius:1-5]");
                int radius = args.size() > 2 ? Arguments.integer(args.get(2), 1, 5, "radius") : 3;
                return territoryMap(actor, radius);
            }
            case "protection" -> {
                args.between(2, 4, "chunk protection [chunkKey|here] [player]");
                String key = chunkKey(actor, args.optional(2, "here"));
                Player target = args.size() > 3 ? resolvePlayer(args.get(3)) : requirePlayer(actor.id());
                ChunkKey parsed = ChunkKey.parse(key);
                Actor subject = target.id.equals(actor.id().toString()) ? actor
                        : new Actor(UUID.fromString(target.id), target.name, false, parsed.dimension(), parsed.x(), parsed.z());
                List<String> result = new ArrayList<>();
                for (AccessAction value : AccessAction.values())
                    result.add(value + "=" + mayActChecked(subject, key, value, value == AccessAction.PVP ? actor.id() : null));
                return "Protection for " + target.name + " at " + key + ":\n" + String.join("\n", result)
                        + "\nexplosions=" + allowsExplosion(key) + "\nPvP diagnostics use the requesting player as the opponent.";
            }
            default -> throw new UserError("Unknown chunk action '" + action + "'. Use help chunks.");
        }
    }

    void claimChunk(Actor actor, Government nation, String key) {
        territory.claim(actor, nation, List.of(key));
    }

    EconomyAccess.Transfer claimFee(Actor actor, Government nation) {
        return territory.claimFee(actor, nation, 1);
    }

    void validateClaim(Actor actor, Government nation, String key) {
        territory.claimPlan(actor, nation, List.of(key));
    }

    void unclaim(Actor actor, String key, boolean force) {
        Claim claim = requiredClaim(key);
        Government nation = force ? null : gov(claim.nationId);
        territory.unclaim(actor, nation, List.of(key), force);
    }

    Claim unclaimPlan(Actor actor, String key, boolean force) {
        Claim claim = requiredClaim(key);
        return territory.unclaimPlan(actor, force ? null : gov(claim.nationId), List.of(key), force).get(0);
    }

    void assertClaimFree(String key, String ignoredTreaty) {
        check(key != null, "Invalid claim key.");
        requireFinancialGuards();
        check(!economy.isClaimEncumbered(key), "This claim is encumbered by an economy listing, loan, or other obligation.");
        check(!commerce.claimLocked(key), "This claim is committed to an active government contract.");
        check(!politics.claimLocked(key, ignoredTreaty), "This claim is committed to an active diplomatic proposal.");
    }

    boolean connected(Set<String> keys) {
        Map<String, Set<ChunkKey>> dimensions = new LinkedHashMap<>();
        for (String key : keys) {
            ChunkKey parsed = ChunkKey.parse(key);
            dimensions.computeIfAbsent(parsed.dimension(), ignored -> new HashSet<>()).add(parsed);
        }
        for (Set<ChunkKey> dimension : dimensions.values()) {
            Set<ChunkKey> unseen = new HashSet<>(dimension);
            Deque<ChunkKey> queue = new ArrayDeque<>();
            ChunkKey first = unseen.iterator().next();
            unseen.remove(first);
            queue.add(first);
            while (!queue.isEmpty()) {
                ChunkKey current = queue.removeFirst();
                for (int[] offset : List.of(new int[]{1, 0}, new int[]{-1, 0}, new int[]{0, 1}, new int[]{0, -1})) {
                    int x = current.x() + offset[0];
                    int z = current.z() + offset[1];
                    if (Math.abs(x) > 1_875_000 || Math.abs(z) > 1_875_000) continue;
                    ChunkKey neighbor = new ChunkKey(current.dimension(), x, z);
                    if (unseen.remove(neighbor)) queue.addLast(neighbor);
                }
            }
            if (!unseen.isEmpty()) return false;
        }
        return true;
    }

    Set<String> cityClaims(String cityId) {
        return data.claims.values().stream().filter(Objects::nonNull).filter(c -> cityId.equals(c.cityId))
                .map(c -> c.key).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    Set<String> stateClaims(String stateId) {
        return data.claims.values().stream().filter(Objects::nonNull).filter(c -> stateId.equals(c.stateId))
                .map(c -> c.key).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    Set<String> nationClaims(String nationId) {
        return data.claims.values().stream().filter(Objects::nonNull).filter(c -> nationId.equals(c.nationId))
                .map(c -> c.key).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String territoryMap(Actor actor, int radius) {
        StringBuilder result = new StringBuilder("Map " + actor.dimension()
                + " (north up): @ you, . wilderness, C your city, N your nation, A ally, W war, # other\n");
        String ownNation = nationOf(actor.id()).orElse(null);
        Player player = requirePlayer(actor.id());
        for (int z = actor.chunkZ() - radius; z <= actor.chunkZ() + radius; z++) {
            for (int x = actor.chunkX() - radius; x <= actor.chunkX() + radius; x++) {
                if (x == actor.chunkX() && z == actor.chunkZ()) { result.append('@'); continue; }
                String key = actor.dimension() + "|" + x + "|" + z;
                Claim claim = data.claims.get(key);
                if (claim == null) { result.append(data.claims.containsKey(key) ? '?' : '.'); continue; }
                if (!viewableClaim(claim)) { result.append('?'); continue; }
                String nation = claim.nationId;
                result.append(claim.cityId != null && Objects.equals(player.cityId, claim.cityId) ? 'C'
                        : nation.equals(ownNation) ? 'N'
                        : "ALLIED".equals(politics.relationStatus(ownNation, nation)) ? 'A'
                        : "WAR".equals(politics.relationStatus(ownNation, nation)) ? 'W' : '#');
            }
            result.append('\n');
        }
        return result.toString();
    }

    String chunkInfo(String key) {
        Claim claim = data.claims.get(key);
        if (claim == null && !data.claims.containsKey(key)) return key + ": wilderness.";
        if (!viewableClaim(claim)) return key + ": invalid claim; protections fail closed. Ask an operator to audit/reassign it.";
        return key + "\nCity: " + governmentName(claim.cityId) + "\nState: " + governmentName(claim.stateId)
                + "\nNation: " + governmentName(claim.nationId) + "\nPrivate title: " + claim.ownerAccount
                + "\nImprovements: " + claim.improvements + "\nClaimed: " + claim.claimedAt
                + "\nEconomy encumbered: " + economy.isClaimEncumbered(key)
                + "\nContract reserved: " + commerce.claimLocked(key)
                + "\nTreaty reserved: " + politics.claimLocked(key, null);
    }

    private String adminCommand(Actor actor, Arguments args) {
        admin(actor);
        String action = args.get(1);
        switch (action) {
            case "bypass" -> {
                args.exactly(3, "admin bypass <on|off>");
                check(Set.of("on", "off").contains(args.get(2)), "Use on or off.");
                Player player = requirePlayer(actor.id());
                boolean enabled = "on".equals(args.get(2));
                if (player.bypass != enabled) {
                    player.bypass = enabled;
                    changed();
                }
                return "Protection bypass " + args.get(2) + "; it is valid only while you remain an operator.";
            }
            case "unclaim" -> {
                args.exactly(3, "admin unclaim <chunkKey|here>");
                String key = chunkKey(actor, args.get(2));
                unclaim(actor, key, true);
                return "Force-unclaimed " + key + ".";
            }
            case "delete" -> {
                args.between(4, 5, "admin delete <nation|state|city|company> <name|id> [cascade]");
                if ("company".equals(args.get(2))) {
                    args.exactly(4, "admin delete company <name|id>");
                    commerce.disband(companyRequired(args.get(3)), true);
                } else {
                    Kind kind;
                    try { kind = Kind.valueOf(args.get(2).toUpperCase(Locale.ROOT)); }
                    catch (IllegalArgumentException e) { throw new UserError("Choose nation, state, city, or company."); }
                    Government government = gov(args.get(3));
                    check(government.kind == kind, "Government level does not match.");
                    if (args.size() == 5) check("cascade".equals(args.get(4)), "Use the literal cascade confirmation.");
                    disband(government, args.size() == 5, true);
                }
                return "Deleted organization after checking financial and active obligations.";
            }
            case "leader" -> {
                args.exactly(4, "admin leader <government> <player>");
                Government government = rawGovernment(args.get(2));
                check(validHierarchy(government), "Repair the hierarchy before appointing a leader.");
                Player player = resolvePlayer(args.get(3));
                if (!member(player.id, government)) administrativeMembership(player, government);
                if (!player.id.equals(government.leader)) appointLeader(government, player.id);
                return "Appointed " + player.name + " as leader.";
            }
            case "rename" -> {
                args.exactly(4, "admin rename <government> <name>");
                Government government = rawGovernment(args.get(2));
                renameGovernment(government, args.get(3));
                history("admin", government.id, "Renamed by " + actor.id());
                return "Government renamed.";
            }
            case "owner" -> {
                args.exactly(4, "admin owner <chunkKey|here> <account>");
                changeProperty(chunkKey(actor, args.get(2)), args.get(3));
                return "Private title reassigned; political hierarchy is unchanged.";
            }
            case "reassign" -> {
                args.exactly(4, "admin reassign <chunkKey|here> <government>");
                String key = chunkKey(actor, args.get(2));
                Territory.Allocation plan = territory.reassignPlan(actor, key, gov(args.get(3)));
                territory.apply(List.of(plan), "admin", actor);
                return "National ownership and optional allocations reassigned; private titles and permits were preserved. Adjacency was explicitly bypassed.";
            }
            case "diagnostics" -> {
                args.between(2, 3, "admin diagnostics [chunkKey|here]");
                return chunkInfo(chunkKey(actor, args.optional(2, "here"))) + "\n"
                        + "Governments=" + data.governments.size() + ", claims=" + data.claims.size()
                        + ", companies=" + data.companies.size() + ", reservations=" + data.shareReservations.size()
                        + ", lastTick=" + data.lastTick + ", economy=" + economy.available();
            }
            case "audit" -> {
                args.between(2, 3, "admin audit [page]");
                return page("Integrity audit", integrity.audit(), args.page(2));
            }
            case "repair" -> {
                args.exactly(3, "admin repair <preview|apply>");
                check(Set.of("preview", "apply").contains(args.get(2)), "Use preview or apply.");
                return "preview".equals(args.get(2)) ? page("Repair preview", integrity.audit(), 1)
                        : integrity.repair();
            }
            case "history" -> {
                args.between(2, 3, "admin history [page]");
                List<History> newest = new ArrayList<>(data.history);
                java.util.Collections.reverse(newest);
                return page("Audit history", newest.stream().filter(Objects::nonNull).map(h -> h.at + " " + h.category + " "
                        + h.entityId + " " + h.text).toList(), args.page(2));
            }
            default -> throw new UserError("Unknown admin action '" + action + "'. Use help admin.");
        }
    }

    private void administrativeMembership(Player player, Government government) {
        Government oldNation = data.governments.get(player.nationId);
        Government oldState = data.governments.get(player.stateId);
        Government oldCity = data.governments.get(player.cityId);
        Government removal = !Objects.equals(player.nationId, nationId(government)) ? oldNation
                : government.kind != Kind.NATION && !Objects.equals(player.stateId,
                government.kind == Kind.STATE ? government.id : government.parentId) ? oldState
                : government.kind == Kind.CITY && !Objects.equals(player.cityId, government.id) ? oldCity : null;
        for (Government scope : ancestors(government)) {
            check(member(player.id, scope) || members(scope).size() < memberLimit(scope.kind),
                    "Citizenship limit reached in " + scope.name + ".");
        }
        if (removal != null) leaveGovernment(player.id, removal);
        setMembership(player, government);
    }

    Government gov(String ref) {
        Government government = rawGovernment(ref);
        check(validHierarchy(government), "Government hierarchy is invalid; an operator must audit/repair it.");
        return government;
    }

    Government rawGovernment(String ref) {
        return findGovernment(ref).orElseThrow(() -> new UserError("Unknown government: " + ref + "."));
    }

    Optional<Government> findGovernment(String ref) {
        if (ref == null) return Optional.empty();
        Government direct = data.governments.get(ref);
        if (direct != null) return Optional.of(direct);
        return data.governments.values().stream().filter(Objects::nonNull)
                .filter(g -> ref.equalsIgnoreCase(g.name) || ref.equalsIgnoreCase(g.id)).findFirst();
    }

    Company companyRequired(String ref) {
        Company company = findCompany(ref).orElseThrow(() -> new UserError("Unknown company: " + ref + "."));
        check(viewableCompany(company), "Company ownership is invalid; ask an operator to audit it.");
        return company;
    }

    Optional<Company> findCompany(String ref) {
        if (ref == null) return Optional.empty();
        Company company = data.companies.get(ref);
        if (company != null) return Optional.of(company);
        return data.companies.values().stream().filter(Objects::nonNull)
                .filter(c -> ref.equalsIgnoreCase(c.name) || ref.equalsIgnoreCase(c.id)).findFirst();
    }

    Player requirePlayer(UUID id) {
        check(id != null, "Specify a player.");
        Player player = data.players.get(id.toString());
        check(player != null && id.toString().equals(player.id), "That player is not known; they must log in first.");
        return player;
    }

    Player resolvePlayer(String ref) {
        Player player = data.players.get(ref);
        if (player != null && validUuid(player.id) && data.players.get(player.id) == player) return player;
        List<Player> matches = data.players.values().stream().filter(Objects::nonNull)
                .filter(p -> validUuid(p.id) && data.players.get(p.id) == p
                        && (ref.equalsIgnoreCase(p.name) || ref.equalsIgnoreCase(p.id))).toList();
        check(matches.size() == 1, matches.isEmpty() ? "Unknown player: " + ref + ". They must log in first."
                : "Ambiguous player name; use their UUID.");
        return matches.get(0);
    }

    Government ownGovernment(Actor actor, Kind kind) {
        Player player = requirePlayer(actor.id());
        String id = switch (kind) { case NATION -> player.nationId; case STATE -> player.stateId; case CITY -> player.cityId; };
        check(id != null, "You do not belong to a " + kind.name().toLowerCase(Locale.ROOT) + ".");
        return gov(id);
    }

    Claim requiredClaim(String key) {
        check(key != null, "Specify a valid chunk key.");
        ChunkKey.parse(key);
        Claim claim = data.claims.get(key);
        check(claim != null, "This chunk is not claimed.");
        return claim;
    }

    boolean validHierarchy(Government government) {
        if (government == null || government.kind == null || !validUuid(government.id)
                || data.governments.get(government.id) != government) return false;
        if (government.kind == Kind.NATION) return government.parentId == null;
        Government parent = data.governments.get(government.parentId);
        if (parent == null || parent == government) return false;
        if (government.kind == Kind.STATE) return parent.kind == Kind.NATION && validHierarchy(parent);
        return parent.kind == Kind.STATE && validHierarchy(parent);
    }

    boolean viewableGovernment(Government government) {
        return validHierarchy(government) && government.name != null && validUuid(government.leader)
                && member(government.leader, government);
    }

    boolean viewableCompany(Company company) {
        return company != null && validUuid(company.id) && data.companies.get(company.id) == company
                && company.name != null && validUuid(company.owner) && data.players.containsKey(company.owner)
                && company.members.contains(company.owner);
    }

    boolean viewableClaim(Claim claim) {
        if (claim == null || claim.key == null || data.claims.get(claim.key) != claim) return false;
        return claimGovernment(claim) != null && claim.ownerAccount != null && canonicalChunk(claim.key);
    }

    Government claimGovernment(Claim claim) {
        if (claim == null) return null;
        Government nation = data.governments.get(claim.nationId);
        if (!validHierarchy(nation) || nation.kind != Kind.NATION) return null;
        if (claim.stateId == null) return claim.cityId == null ? nation : null;
        Government state = data.governments.get(claim.stateId);
        if (!validHierarchy(state) || state.kind != Kind.STATE || !nation.id.equals(state.parentId)) return null;
        if (claim.cityId == null) return state;
        Government city = data.governments.get(claim.cityId);
        return validHierarchy(city) && city.kind == Kind.CITY && state.id.equals(city.parentId) ? city : null;
    }

    boolean claimInGovernment(Claim claim, Government government) {
        if (claimGovernment(claim) == null || !validHierarchy(government)) return false;
        return switch (government.kind) {
            case NATION -> government.id.equals(claim.nationId);
            case STATE -> government.id.equals(claim.stateId);
            case CITY -> government.id.equals(claim.cityId);
        };
    }

    boolean publicTitle(Claim claim) {
        if (claim == null || claim.ownerAccount == null) return false;
        String[] parts = claim.ownerAccount.split(":", 2);
        return parts.length == 2 && Set.of("nation", "state", "city").contains(parts[0]) && validUuid(parts[1]);
    }

    boolean member(String playerId, Government government) {
        Player player = data.players.get(playerId);
        if (player == null || !validHierarchy(government)) return false;
        return switch (government.kind) {
            case NATION -> government.id.equals(player.nationId);
            case STATE -> government.id.equals(player.stateId) && government.parentId.equals(player.nationId);
            case CITY -> government.id.equals(player.cityId) && government.parentId.equals(player.stateId)
                    && nationId(government).equals(player.nationId);
        };
    }

    Set<String> members(Government government) {
        return data.players.values().stream().filter(Objects::nonNull).filter(p -> validUuid(p.id) && member(p.id, government))
                .map(p -> p.id).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    int memberLimit(Kind kind) {
        return switch (kind) {
            case NATION -> config.maxMembersPerNation;
            case STATE -> config.maxMembersPerState;
            case CITY -> config.maxMembersPerCity;
        };
    }

    List<Government> ancestors(Government government) {
        List<Government> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Government current = government; current != null && result.size() < 3 && seen.add(current.id);
             current = data.governments.get(current.parentId)) result.add(current);
        return result;
    }

    List<Government> subtree(Government government) {
        List<Government> result = new ArrayList<>();
        result.add(government);
        for (int i = 0; i < result.size(); i++) {
            String parent = result.get(i).id;
            for (Government candidate : data.governments.values()) {
                if (candidate != null && Objects.equals(parent, candidate.parentId) && !result.contains(candidate))
                    result.add(candidate);
            }
        }
        return result;
    }

    String nationId(Government government) {
        if (government.kind == Kind.NATION) return government.id;
        if (government.kind == Kind.STATE) return government.parentId;
        Government state = data.governments.get(government.parentId);
        return state == null ? null : state.parentId;
    }

    void manage(Actor actor, Government government) {
        check(canManage(actor, government), "You are not an authorized official of this government or its ancestors.");
    }

    boolean canManage(Actor actor, Government government) {
        return actor.admin() || mayManageGovernment(actor.id(), government.id);
    }

    void executive(Actor actor, Government government) {
        check(actor.admin() || ancestors(government).stream().anyMatch(scope ->
                        actor.id().toString().equals(scope.leader) && member(scope.leader, scope)),
                "Only this government's leader or a superior leader may perform that action.");
    }

    boolean mayRemoveMembership(Actor actor, String player, Government government) {
        if (actor.admin()) return true;
        String caller = actor.id().toString();
        return ancestors(government).stream().skip(1)
                .filter(scope -> member(player, scope)
                        && (player.equals(scope.leader) || scope.officers.contains(player)))
                .allMatch(scope -> (caller.equals(scope.leader) && member(caller, scope))
                        || ancestors(scope).stream().skip(1).anyMatch(superior -> member(caller, superior)
                        && (caller.equals(superior.leader) || superior.officers.contains(caller))));
    }

    boolean requiresNationalLegislation(Actor actor, Government government, String key) {
        return config.requireLegislationForPolicy && government.kind == Kind.NATION
                && !actor.admin() && !"open".equals(key);
    }

    void admin(Actor actor) {
        check(actor.admin(), "This command requires operator permission.");
    }

    String role(String player, Government government) {
        if (Objects.equals(government.leader, player)) return switch (government.kind) {
            case NATION -> "leader";
            case STATE -> "governor";
            case CITY -> "mayor";
        };
        if (government.officers.contains(player)) return "officer";
        return "citizen";
    }

    Map<String, String> settings(Government government) {
        return settings(government, null, null);
    }

    Map<String, String> previewSettings(Government government, String key, String value) {
        if ("inherit".equals(value)) check(GovernanceSettings.defaults(config).containsKey(key), "Unknown government policy.");
        else GovernanceSettings.validate(key, value);
        return settings(government, key, value);
    }

    private Map<String, String> settings(Government government, String overrideKey, String overrideValue) {
        Map<String, String> result = GovernanceSettings.defaults(config);
        List<Government> ancestors = ancestors(government);
        java.util.Collections.reverse(ancestors);
        for (Government current : ancestors) {
            for (Map.Entry<String, String> entry : current.settings.entrySet()) {
                if (current == government && overrideKey != null && overrideKey.equals(entry.getKey())) continue;
                if (current == government || GovernanceSettings.inherited(entry.getKey())) {
                    try { result.put(entry.getKey(), GovernanceSettings.validate(entry.getKey(), entry.getValue())); }
                    catch (UserError ignored) { /* Corrupt policies remain auditable and never become effective. */ }
                }
            }
            if (current == government && overrideKey != null && !"inherit".equals(overrideValue))
                result.put(overrideKey, GovernanceSettings.validate(overrideKey, overrideValue));
            GovernanceData.Emergency emergency = data.emergencies.get(current.id);
            if (emergency != null && emergency.expiresAt > now()
                    && (current == government || GovernanceSettings.inherited(emergency.policy))) {
                try { result.put(emergency.policy, GovernanceSettings.validate(emergency.policy, emergency.value)); }
                catch (UserError ignored) { }
            }
        }
        return Map.copyOf(result);
    }

    boolean flag(Government government, String key) {
        return Boolean.parseBoolean(settings(government).get(key));
    }

    void setPolicy(Government government, String key, String value) {
        if ("inherit".equals(value)) {
            check(GovernanceSettings.defaults(config).containsKey(key), "Unknown government policy.");
            if (government.settings.containsKey(key)) {
                government.settings.remove(key);
                changed();
            }
        } else {
            String validated = GovernanceSettings.validate(key, value);
            if (!Objects.equals(government.settings.put(key, validated), validated)) changed();
        }
    }

    String account(Government government) {
        return government.kind.name().toLowerCase(Locale.ROOT) + ":" + government.id;
    }

    void validateOwnerAccount(String account) {
        check(account != null, "Specify an owner account.");
        String[] parts = account.split(":", 2);
        check(parts.length == 2 && validUuid(parts[1]), "Owner accounts must reference a known player, government or company UUID.");
        if ("player".equals(parts[0])) {
            requirePlayer(UUID.fromString(parts[1]));
        } else if ("company".equals(parts[0])) {
            Company company = companyRequired(parts[1]);
            check(account.equals("company:" + company.id), "Invalid company account.");
        } else {
            Government government = gov(parts[1]);
            check(account.equals(account(government)), "Account prefix does not match its government level.");
        }
    }

    boolean eligibleOwner(Claim claim, String account) {
        Government jurisdiction = claimGovernment(claim);
        if (jurisdiction == null) return false;
        if (flag(jurisdiction, "foreignProperty")) return true;
        String[] parts = account.split(":", 2);
        String nation;
        if ("player".equals(parts[0])) nation = nationOf(UUID.fromString(parts[1])).orElse(null);
        else if ("company".equals(parts[0])) nation = nationOf(UUID.fromString(data.companies.get(parts[1]).owner)).orElse(null);
        else nation = nationId(data.governments.get(parts[1]));
        return Objects.equals(claim.nationId, nation);
    }

    void assertAccountDisposable(String account) {
        requireFinancialGuards();
        check(!economy.isAccountInUse(account), "This account is in use by the economy; settle its obligations first: " + account);
        check(!economy.available() || economy.balance(account) == 0, "Empty the account before deleting its owner: " + account);
    }

    void requireFinancialGuards() {
        check(!data.economySeen || economy != EconomyAccess.UNAVAILABLE,
                "Restore StateCraft Economy or its persistent financial-lock adapter before changing existing titles/account owners.");
    }

    void requireEconomy(long cents) {
        Money.nonNegative(cents);
        check(cents == 0 || economy.available(), "This action requires StateCraft Economy.");
    }

    void pay(List<EconomyAccess.Transfer> transfers) {
        List<EconomyAccess.Transfer> nonzero = new ArrayList<>();
        for (EconomyAccess.Transfer transfer : transfers) {
            requireEconomy(transfer.cents());
            if (transfer.cents() > 0) nonzero.add(transfer);
        }
        if (!nonzero.isEmpty()) {
            economy.transferBatch(List.copyOf(nonzero));
            changed();
        }
    }

    long now() {
        return Math.max(data.lastTick, Math.max(0, clock.getAsLong()));
    }

    static long deadline(long start, long duration) {
        return duration > Long.MAX_VALUE - start ? Long.MAX_VALUE : start + duration;
    }

    static String newId() {
        return UUID.randomUUID().toString();
    }

    static boolean validUuid(String value) {
        if (value == null) return false;
        try { return UUID.fromString(value).toString().equals(value); }
        catch (IllegalArgumentException e) { return false; }
    }

    private static boolean canonicalChunk(String value) {
        if (value == null) return false;
        try { return value.equals(ChunkKey.parse(value).toString()); }
        catch (UserError error) { return false; }
    }

    String validGovernmentName(String value, String currentId) {
        String name = organizationName(value);
        check(data.governments.values().stream().filter(Objects::nonNull).noneMatch(g ->
                !Objects.equals(currentId, g.id) && name.equalsIgnoreCase(g.name)), "That government name is already in use.");
        return name;
    }

    String organizationName(String value) {
        String name = text(value, config.maxNameLength, "Name").strip();
        check(name.length() >= 3 && name.matches("[\\p{L}\\p{N}][\\p{L}\\p{N} _.'-]*"),
                "Names need at least 3 characters and may contain letters, digits, spaces, _, ., ' and -.");
        check(!validUuid(name) && !Set.of("all", "here", "inherit", "none").contains(name.toLowerCase(Locale.ROOT)),
                "That name is reserved.");
        return name;
    }

    static String text(String value, int max, String label) {
        check(value != null && !value.isBlank() && value.length() <= max,
                label + " must contain 1-" + max + " characters.");
        check(value.chars().noneMatch(Character::isISOControl), label + " cannot contain control characters.");
        return value;
    }

    static String prose(String value, int max, String label) {
        check(value != null && !value.isBlank() && value.length() <= max,
                label + " must contain 1-" + max + " characters.");
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
        check(normalized.chars().noneMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\t'),
                label + " may contain line breaks and tabs, but no other control characters.");
        return normalized;
    }

    public synchronized Optional<String> knownPlayerName(UUID id) {
        Player player = data.players.get(id.toString());
        return player == null ? Optional.empty() : Optional.ofNullable(player.name);
    }

    String playerName(String id) {
        Player player = data.players.get(id);
        return player == null || player.name == null ? String.valueOf(id) : player.name;
    }

    String governmentName(String id) {
        if (id == null) return "unassigned";
        Government government = data.governments.get(id);
        return government == null ? "[deleted " + id + "]" : government.name;
    }

    String companyName(String id) {
        Company company = data.companies.get(id);
        return company == null ? "[deleted " + id + "]" : company.name;
    }

    String chunkKey(Actor actor, String ref) {
        return "here".equals(ref) ? actor.chunkKey() : ChunkKey.parse(ref).toString();
    }

    String governmentInfo(Government government) {
        return government.kind + " " + government.name + " [" + government.id + "]"
                + "\nLeader: " + playerName(government.leader) + "\nParent: "
                + (government.parentId == null ? "none" : governmentName(government.parentId))
                + "\nCitizens: " + members(government).size() + "\nOfficers: " + government.officers.size()
                + "\nTerritory: " + data.claims.values().stream().filter(Objects::nonNull)
                        .filter(c -> claimInGovernment(c, government)).count() + " chunk(s)"
                + "\nTag: " + government.tag + "\nFlag: " + government.flag
                + "\nDescription: " + government.description + "\nTreasury: " + account(government);
    }

    String summary() {
        return "StateCraft: " + data.governments.size() + " governments, " + data.claims.size() + " claims, "
                + data.companies.size() + " companies. Economy: " + (economy.available() ? "available" : "not installed/ready")
                + ".\nUse help for sections, or help <section> [page]."
                + (validationProblems.isEmpty() ? "" : "\nREPAIR REQUIRED: " + validationProblems.size()
                + " data issue(s); scheduled workflows are paused. No records were discarded. Use admin audit.");
    }

    String page(String title, List<String> rows, int page) {
        int budget = config.maxCommandOutput - Math.min(config.maxCommandOutput / 2, title.length() + 120);
        int widest = rows.stream().filter(Objects::nonNull).mapToInt(String::length).max().orElse(0);
        int perPage = Math.min(config.pageSize, (int) Math.max(1, budget / Math.max(1L, (long) widest + 1)));
        int pages = Math.max(1, (rows.size() + perPage - 1) / perPage);
        check(page >= 1 && page <= pages, "Page must be between 1 and " + pages + ".");
        int from = Math.min(rows.size(), (page - 1) * perPage);
        int to = Math.min(rows.size(), from + perPage);
        return title + " (" + page + "/" + pages + ", " + rows.size() + " total)\n"
                + (rows.isEmpty() ? "(none)" : String.join("\n", rows.subList(from, to)));
    }

    String bounded(String output) {
        if (output.length() <= config.maxCommandOutput) return output;
        return output.substring(0, config.maxCommandOutput - 60)
                + "\n[Output truncated; use a narrower query or another page.]";
    }

    void history(String category, String entity, String text) {
        appendHistory(data.history, now(), category, entity, text);
    }

    void appendHistory(List<History> list, long at, String category, String entity, String text) {
        History entry = new History();
        entry.at = at;
        entry.category = category;
        entry.entityId = entity;
        entry.text = text.length() > 1_000 ? text.substring(0, 1_000) : text;
        changed();
        list.add(entry);
        trim(list, config.maxHistory);
    }

    <T> void trim(List<T> list, int limit) {
        if (list.size() > limit) {
            list.subList(0, list.size() - limit).clear();
            changed();
        }
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new UserError(message);
    }

    private static Arguments prefixed(String first, String second, List<String> tail) {
        List<String> words = new ArrayList<>(List.of(first, second));
        words.addAll(tail);
        return new Arguments(List.copyOf(words));
    }

    private GovernmentView view(Government government) {
        return new GovernmentView(government.id, government.kind, government.name, government.parentId, nationId(government),
                UUID.fromString(government.leader), Set.copyOf(members(government).stream().map(UUID::fromString).toList()),
                settings(government));
    }

    private CompanyView view(Company company) {
        Map<UUID, Long> shares = new LinkedHashMap<>();
        company.shares.forEach((id, value) -> { if (validUuid(id) && value != null && value > 0) shares.put(UUID.fromString(id), value); });
        return new CompanyView(company.id, company.name, UUID.fromString(company.owner),
                Set.copyOf(company.members.stream().filter(GovernanceEngine::validUuid).map(UUID::fromString).toList()),
                Map.copyOf(shares));
    }

    private ClaimView view(Claim claim) {
        ChunkKey key = ChunkKey.parse(claim.key);
        return new ClaimView(claim.key, key.dimension(), key.x(), key.z(), claim.cityId, claim.stateId,
                claim.nationId, claim.ownerAccount, claim.improvements, claim.claimedAt);
    }
}
