package dev.statecraft.domain;

import dev.statecraft.api.UserError;

import java.util.List;
import java.util.Locale;

final class GovernanceHelp {
    private GovernanceHelp() {}

    static List<String> lines(String section) {
        return switch (section.toLowerCase(Locale.ROOT)) {
            case "overview", "main", "help" -> List.of(
                    "Prefix commands with /sc or /statecraft. Quote names or text containing spaces.",
                    "Quoted narrative fields support escaped \\n and \\t (or GUI multiline input); names, titles and mail subjects remain single-line.",
                    "help governments [page] — nation/state/city hierarchy, citizenship, officials and settings.",
                    "help chunks [page] — claims, permits, automatic claiming, maps and protection.",
                    "help elections [page] — candidate registration, voting and results.",
                    "help legislature [page] — bills, amendments, voting, signatures, vetoes and laws.",
                    "help executive [page] — temporary emergency policies and cooldowns.",
                    "help diplomacy [page] — alliances, wars, peace terms, ratification and truces.",
                    "help companies [page] — employment, shares, ownership and shareholder proposals.",
                    "help contracts [page] — government procurement, bids, escrow and completion review.",
                    "help mail [page] — personal/official mail and private invitations.",
                    "help profiles [page] — public player profiles and world information.",
                    "help admin [page] — operator bypass, reassignment, diagnostics, audit and safe repair.",
                    "Government/company references accept names or UUIDs. Players must have logged in; use names or known UUIDs.",
                    "Amounts use dollars with at most two decimal places; settings baseChunkValue uses integer cents.",
                    "Chunk keys are dimension|x|z (for example minecraft:overworld|0|0); 'here' means your current chunk.",
                    "Most lists accept a final page number. Output, histories, mail and pending workflows have configured bounds.");
            case "governments", "government", "nation", "nations", "state", "states", "city", "cities", "members", "officers", "invitations" -> List.of(
                    "nation create <name> [leader: operator only] — found a nation; founder must be unaffiliated.",
                    "state create <nation> <name> [governor] — parent official; nominee must be a parent citizen without a state.",
                    "city create <state> <name> [mayor] — parent official; nominee must be a parent citizen without a city.",
                    "<nation|state|city|government> list [page] — public, paginated government directory.",
                    "<kind> info [government] — metadata, parent, citizens, leader and treasury.",
                    "<kind> members|roles|officers <government> [page] — public roles and citizenship.",
                    "<kind> join <government> — open membership or an invitation; join the parent first.",
                    "<kind> invite <government> <player>; <kind> accept <government> — officials invite, invitees accept.",
                    "<kind> invitations <government> [page] — own invitations, or all for authorized officials.",
                    "<kind> revoke <government> <player>; <kind> decline <government> — revoke/decline a pending invitation.",
                    "<kind> leave [government]; <kind> kick <government> <player> — remove subordinate memberships; transfer all affected leadership first.",
                    "<kind> leader|appoint <government> <citizen> — current/superior leader transfers the office; no equity/title changes.",
                    "<kind> officer <government> <citizen> <add|remove> — current/superior leader manages officers.",
                    "<kind> rename|description|tag|flag <government> <text> — officials edit metadata; names are unique across government levels.",
                    "<kind> settings <government> — effective typed policy values; taxes/open are local, other policies inherit.",
                    "<kind> setting <government> <key> <value|inherit> — executive policy, subject to requireLegislationForPolicy.",
                    "Rate policies: incomeTaxBps, salesTaxBps, propertyTaxBps, tariffBps, corporateTaxBps; integers 0-10000.",
                    "Money policy: baseChunkValue, a nonnegative integer in cents (not a dollar amount).",
                    "Boolean policies: open, memberAccess, foreignAccess, alliedAccess, enemyAccess, pvp, explosions, foreignProperty, friendlyFire, citizenLegislature, peaceRatification.",
                    "<kind> disband <government> [cascade] — executive; populated trees need explicit cascade. Private titles, funds and obligations are guarded.",
                    "Nation officials manage only their descendants; governors manage their state/cities; mayors only their city. Officers cannot appoint leaders.",
                    "Account permissions inherit within that same hierarchy. Operator authority is checked from the executing Actor, never inferred from a UUID.");
            case "chunks", "chunk", "claims", "map" -> List.of(
                    "chunk claim [city] [chunkKey|here] (alias: claim) — authorized official; limits, fees and per-dimension adjacency apply.",
                    "chunk unclaim [chunkKey|here] (alias: unclaim) — city authority plus private title authority; cannot split connected territory.",
                    "chunk autoclaim <on|off> — moving claims for your resident city, using the same checks and fees.",
                    "chunk info [chunkKey|here] — political hierarchy, independent private title, improvements and reservations.",
                    "chunk list [government|all] [page] — paginated claims within the selected subtree.",
                    "chunk permit <chunkKey|here> <player> <all|none|ACTION,ACTION> — private owner/manager grants chunk-specific permissions.",
                    "Permit actions: BREAK, PLACE, BLOCK_INTERACT, ENTITY_INTERACT, ATTACK; permits never bypass player-PvP restrictions.",
                    "chunk permits [chunkKey|here] [page] — title holder's permit list.",
                    "chunk protection [chunkKey|here] [player] — access diagnostics; PvP uses requester as opponent.",
                    "chunk map [radius:1-5] (alias: map) — local dimension map; @ you, C resident city, N nation, A ally, W enemy, # other, . wilderness.",
                    "New claims are privately owned by city:<UUID>; buying property never changes nation/state/city boundaries.",
                    "Private property ignores general public/diplomatic build access; only title managers and explicit permits enter/build.",
                    "War can override a no-PvP policy on signatory land when configured; friendly/allied/truce PvP is protected by default.",
                    "Claims pledged to contracts, treaties, stock/economy property listings or loans cannot be unclaimed or retitled.");
            case "elections", "election" -> List.of(
                    "election list [page] — all nations, IDs and election phases; no citizenship required.",
                    "election status [nation] — registration/voting phase and deadlines.",
                    "election candidates <nation> [page] — eligible registered candidates.",
                    "election candidate|register <nation> — current citizen; candidate fee requires economy when nonzero.",
                    "election withdraw <nation> — withdraw during registration; candidate fees are not refunded.",
                    "election vote <nation> <candidate> — one ballot per eligible citizen; electorate is frozen when voting opens.",
                    "election history <nation> [page] — bounded results and schedule history.",
                    "election start|close|cancel <nation> — operator-only early voting, closing or cancellation.",
                    "Election winners are automatically appointed. Ties use ascending UUID; no candidates or valid ballots retain the incumbent.",
                    "Already cast ballots survive expulsion. Candidates must remain citizens. Large time jumps skip missed empty cycles without loops.");
            case "legislature", "bill", "bills", "laws", "law" -> List.of(
                    "bill list [nation|all] [page] — debate, voting, passed, vetoed and concluded bills, with nation and bill IDs.",
                    "bill propose <nation> <policy|roleplay> <value|-> <title> <text> — introduce a typed policy or explicitly roleplay-only law.",
                    "bill amend <nation> <policy|roleplay> <value|-> <title> <text> — constitutional amendment; whole-electorate supermajority required.",
                    "bill info|status <billId> — full bill, deadlines and tally.",
                    "bill revise <billId> <value> <title> <text> — author during debate; restarts the full debate period.",
                    "bill vote <billId> <yes|no|abstain> — one vote per eligible legislator per ballot.",
                    "bill votes <billId> [page] — public legislative roll call.",
                    "bill sign <billId> — national leader signs a passed bill into law.",
                    "bill veto <billId> <reason> — national leader vetoes a passed bill.",
                    "bill override <billId> — legislator opens an override ballot; supermajority automatically enacts it.",
                    "bill cancel <billId> — author during debate, or operator; treaty bills cannot be withdrawn by their nominal author.",
                    "bill history <billId> [page] — bounded legislative history.",
                    "law list [nation|all] [page]; law read <nation> <lawId> — retained enacted law codex, with nation and law IDs.",
                    "Quorum counts yes/no/abstain against the frozen electorate; ordinary bills require yes > no. Amendments/overrides need configured whole-electorate thresholds.",
                    "Legislators are the leader and national officers, plus citizens if citizenLegislature=true. Operator status never manufactures a ballot.",
                    "Unsigned bills and unused veto-override windows expire. Roleplay laws are recorded but cannot alter mechanics.",
                    "Enacted settings remain effective even when old codex/history entries age out; later laws on the same setting supersede earlier ones.");
            case "executive" -> List.of(
                    "executive status <nation> — active temporary order and last emergency use.",
                    "executive emergency <nation> <policy> <value> <reason> — national leader; one temporary typed policy at a time.",
                    "executive rescind <nation> — end the temporary order early; cooldown still applies.",
                    "Orders overlay, rather than overwrite, permanent policies. Expiry reveals the current permanent law, including laws passed during the emergency.");
            case "diplomacy" -> List.of(
                    "diplomacy status [nation|all] [page]; diplomacy proposals [nation|all] [page] — relations, truces, nation IDs and proposal IDs.",
                    "diplomacy alliance <fromNation> <toNation> [message] — national leader proposes an alliance while neutral.",
                    "diplomacy accept <proposalId> — receiving nation's leader accepts an alliance or negotiated peace.",
                    "diplomacy break <fromNation> <ally> — leader ends an alliance; any post-war truce remains binding.",
                    "diplomacy war <fromNation> <toNation> [reason] — leader declares war; cannot attack an ally or violate a truce.",
                    "diplomacy peace <fromNation> <toNation> <offerAmount> <demandAmount> <chunk=destinationCity,...|-> [message] — propose immutable peace terms.",
                    "diplomacy truce <fromNation> <toNation> — shorthand for a zero-money, no-territory peace proposal, not unilateral war termination.",
                    "diplomacy terms|info <proposalId> [page] — monetary/chunk terms, expiry, ratifications and associated bill IDs.",
                    "diplomacy ratify <proposalId> <yes|no|abstain> — vote in your currently open legislative ratification ballot (same as bill vote).",
                    "diplomacy execute <proposalId> — either signatory leader retries a ratified settlement blocked by unavailable funds/terms.",
                    "diplomacy reject <proposalId> — receiving executive rejects; diplomacy cancel <proposalId> — proposing executive withdraws.",
                    "Money moves only between the two nation treasuries. A single transferBatch settles all payments before territory/war state changes.",
                    "Only public city-owned claims can transfer; terms must preserve configured city connectivity/limits. Selected claims remain reserved until conclusion/expiry.",
                    "If ratification is required by configuration or either nation, acceptance creates a debate/vote bill in each legislature. Both must pass before expiry.",
                    "A failed or expired treaty leaves war and money unchanged. Successful peace applies a binding configured truce and sends official notifications.");
            case "companies", "company", "shareholders" -> List.of(
                    "company create <name>; company list [page]; company info <company> — incorporation and directory.",
                    "company members <company> [page]; company shareholders <company> [page] — employment is separate from equity.",
                    "company invite <company> <player>; company accept|join <company> — invitation-only employment.",
                    "company revoke <company> <player>; company decline <company> — manage invitations.",
                    "company leave <company>; company kick <company> <player> — shares/reservations survive employment changes; owner must transfer office first.",
                    "company officer <company> <member> <add|remove> — owner appoints company treasury managers.",
                    "company owner <company> <memberShareholder> — management ownership transfer; blocked while account or government contracts are in use.",
                    "company rename|description <company> <text> — edit company metadata.",
                    "company transfer <company> <player> <shares> — transfer only your unreserved shares; recipient must be known.",
                    "company propose <company> <owner|dividend|roleplay|dissolve> <value|-> <title> <text> — shareholder introduces a weighted ballot.",
                    "For owner proposals value is a member shareholder; dividends use a total dollar amount; roleplay/dissolve use '-'.",
                    "company proposals <company|all> [page]; company proposal <proposalId> — company/proposal IDs, votes, deadlines and retained decisions.",
                    "company vote <proposalId> <yes|no|abstain> — one vote weighted by holdings at proposal creation (including reserved shares).",
                    "company execute <proposalId> — participant retries approved settlement within one voting-duration after the ballot closes.",
                    "company cancel <proposalId> — author before any vote, or operator.",
                    "company disband <company> — sole shareholder owner; otherwise a passed dissolve proposal is required. All funds/property/obligations/reservations must be clear.",
                    "Dividends use current shares at payment, rounded by largest remainder then UUID; all recipients are paid in one atomic batch.",
                    "Stock listing reservations persist, support partial settlement, and cannot be transferred twice. Listing cancellation releases only the unsold reservation.");
            case "contracts", "contract" -> List.of(
                    "contract list [government|all] [page]; contract my [page] — public directory or your submitted/issued work.",
                    "contract create <government> <title> <description> <chunkKey,...|here> — official selects government-owned chunks in its subtree.",
                    "contract info <contractId> [page] — terms, status, selected chunks, awarded payee and escrow.",
                    "contract bid <contractId> <amount> <description> [company:nameOrId] — bidder's own player account or a company they manage; nonzero bids require economy.",
                    "contract withdraw <contractId> — remove your unawarded bid before selection.",
                    "contract bids <contractId> [page] — authorized officials review bids privately.",
                    "contract review <contractId> — official closes bidding early and opens review.",
                    "contract award <contractId> <bidder> — independent official chooses a reviewed bid and deposits the entire payment into contract escrow.",
                    "contract submit <contractId> <completionNote> — winner/company manager submits completed work.",
                    "contract return <contractId> <reason> — independent official requests corrections without changing escrow.",
                    "contract complete <contractId> — independent authorized official approves submitted work; escrow pays the original authorized payee.",
                    "contract cancel <contractId> <reason> — issuer or selected contractor cancels; escrow refunds only the issuing government.",
                    "Paid bidders, submitting users and beneficiaries cannot approve their own work, even with operator permission.",
                    "No-bid contracts and unawarded review windows expire. Awarded/submitted escrow never disappears on timeout; cancel or complete it explicitly.",
                    "Selected chunks remain reserved while active; account/title deletion or political transfer cannot orphan a contract.");
            case "mail", "official_mail" -> List.of(
                    "mail inbox [page]; mail sent [page]; mail read <messageId> — your own persistent messages only.",
                    "mail send|compose <player|government:nameOrId> <subject> <body> — personal correspondence to a known player or official mailbox.",
                    "mail reply <messageId> <body> — reply to a personal inbox sender; system notifications cannot receive replies.",
                    "mail delete <messageId> — deletes only your copies, never the recipient's copy.",
                    "mail invitations [page] — only your private government/company invitations.",
                    "mail official inbox|sent <government> [page] — authorized local/inherited officials, or operators.",
                    "mail official read <government> <messageId> — scoped official mailbox message view.",
                    "mail official send|compose <government> <player|government:nameOrId> <subject> <body> — compose as an official; sender is verified and audited.",
                    "mail official reply <government> <messageId> <body>; mail official delete <government> <messageId>.",
                    "Subjects are at most 100 characters, bodies follow maxMailBodyLength. Oldest inbox/sent copies age out independently.",
                    "Operators cannot use personal mail commands to read somebody else's mail; official mailbox access is separately authorized.");
            case "profiles", "profile", "info" -> List.of(
                    "profile [player] — public identity, nation/state/city offices, company participation and shares.",
                    "Only your own profile includes your unread personal-mail count; other profiles never expose mail.",
                    "info — world organization counts and economy availability.");
            case "admin" -> List.of(
                    "All commands below require Actor.admin=true on that invocation.",
                    "admin bypass <on|off> — explicit protection override; every action rechecks current operator status, so a stale flag cannot grant access.",
                    "admin unclaim <chunkKey|here> — force removal without adjacency/private-title checks; financial and active workflow reservations still apply.",
                    "admin delete <nation|state|city> <government> [cascade] — explicit cascade for populated trees; funds/encumbrances/active obligations remain guarded.",
                    "admin delete company <company> — forced dissolution still protects shares reserved in stock listings, property, funds and active obligations.",
                    "admin leader <government> <knownPlayer> — explicit appointment, safely adjusting membership; cannot orphan another leadership office.",
                    "admin rename <government> <name> — force unique valid name.",
                    "admin reassign <chunkKey|here> <city> — political reassignment, intentionally ignoring adjacency; private non-city titles are preserved.",
                    "admin owner <chunkKey|here> <account> — private-title reassignment; validates references, player foreign-property eligibility and encumbrances.",
                    "admin diagnostics [chunkKey|here] — claim ownership/locks, counts, clock and economy state.",
                    "admin audit [page] — read-only consistency report.",
                    "Malformed loads are retained in repair-required mode; timers and ordinary mutations pause until structural issues are explicitly resolved.",
                    "admin repair preview — read-only repair preview; admin repair apply — explicitly perform only safe orphan cleanup.",
                    "Repair clears dangling memberships/stale officers/invites and financially inert orphan public claims/governments. It never invents shares, releases stock reservations or discards escrow.",
                    "Private orphan claims and ambiguous leaders/IDs remain for explicit reassignment/appointment. Settle financial obligations through the economy before deleting anything.",
                    "admin history [page] — bounded governance audit trail.",
                    "An unavailable-economy adapter may supply persistent funded-account/collateral locks; these remain enforced without blocking verified unencumbered cleanup.",
                    "Core domain does not reload Forge configuration files; the runtime calls configure(validated GovernanceConfig).");
            default -> throw new UserError("Unknown help section '" + section + "'. Use help.");
        };
    }
}
