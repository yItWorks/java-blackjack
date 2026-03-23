package blackjack.controller;

import blackjack.domain.judgement.Answer;
import blackjack.domain.judgement.BettingMoney;
import blackjack.domain.judgement.BettingMoneyInfo;
import blackjack.domain.judgement.Profit;
import blackjack.domain.participant.Dealer;
import blackjack.domain.participant.Nickname;
import blackjack.domain.participant.Participants;
import blackjack.domain.participant.Player;
import blackjack.domain.participant.Players;
import blackjack.domain.card.Trump;
import blackjack.domain.judgement.ProfitCalculator;
import blackjack.strategy.ShuffleStrategy;
import blackjack.utils.Parser;
import blackjack.utils.RetryExecutor;
import blackjack.view.InputView;
import blackjack.view.OutputView;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BlackjackController {

    private final ShuffleStrategy shuffleStrategy;

    public BlackjackController(ShuffleStrategy shuffleStrategy) {
        this.shuffleStrategy = shuffleStrategy;
    }

    public void run() {
        Dealer dealer = readyGame();
        Players players = readPlayers();
        Participants participants = createParticipants(dealer, players);
        BettingMoneyInfo bettingMoneyInfo = readBettingMoney(players);

        gameStart(dealer, players);

        printResult(participants, bettingMoneyInfo);
    }

    private Dealer readyGame() {
        Trump trump = new Trump(shuffleStrategy);
        return new Dealer(trump);
    }

    private Players readPlayers() {
        return RetryExecutor.retry(this::readPlayersName);
    }

    private Players readPlayersName() {
        String rawNicknames = InputView.readNicknames();
        List<String> nicknames = Parser.parseNickname(rawNicknames);
        List<Player> players = nicknames.stream()
                .map(Player::new)
                .toList();
        return new Players(players);
    }

    private static Participants createParticipants(Dealer dealer, Players players) {
        return new Participants(dealer, players);
    }

    private BettingMoneyInfo readBettingMoney(Players players) {
        Map<Nickname, BettingMoney> bettingMoneyByPlayer = new HashMap<>();
        players.all().forEach(player -> {
            BettingMoney bettingMoney = RetryExecutor.retry(this::readBettingMoneyByPlayer, player);
            bettingMoneyByPlayer.put(player.getNickname(), bettingMoney);
        });

        return new BettingMoneyInfo(bettingMoneyByPlayer);
    }

    private BettingMoney readBettingMoneyByPlayer(Player player) {
        String rawBettingMoney = InputView.readBettingMoney(player.getNickname().toString());
        return new BettingMoney(rawBettingMoney);
    }

    private void gameStart(Dealer dealer, Players players) {
        // 딜러가 2장씩 배부
        dealer.pitch(players.all());
        // 카드 정보 출력
        OutputView.printStartMessage(players.all(), dealer);
        // 블랙잭 플레이어 처리
        players.all().forEach(this::handleBlackjack);
        // 플레이어 액션 처리
        players.all().forEach(player -> handlePlayerAction(player, dealer));
        // 딜러 액션 처리
        dealer.playTurn(OutputView::printDealerHitMessage);
    }

    private void handleBlackjack(Player player) {
        player.handleBlackjack();
    }

    private void handlePlayerAction(Player player, Dealer dealer) {
        while (player.isHit()) {
            Answer answer = RetryExecutor.retry(this::readAnswer, player.getNickname().toString());

            answer.ifYes(() -> playerDrawCard(player, dealer));
            answer.ifNo(player::stay);
            
            OutputView.printCardStatus(player);
        }
    }

    private Answer readAnswer(final String nickname) {
        return Answer.pick(InputView.readAnswer(nickname));
    }

    private static void playerDrawCard(Player player, Dealer dealer) {
        dealer.giveCard(player);
        player.handleBurst();
    }

    private void printResult(Participants participants, BettingMoneyInfo bettingMoneyInfo) {
        OutputView.printFinalStatus(participants);
        Map<Nickname, Profit> playerProfit = ProfitCalculator.calculatePlayerProfit(participants, bettingMoneyInfo);
        Profit dealerProfit = ProfitCalculator.calculateDealerProfit(playerProfit);
        OutputView.printProfit(playerProfit, dealerProfit);
    }
}
