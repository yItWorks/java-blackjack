package blackjack.domain.participant;

import java.util.ArrayList;
import java.util.List;

public record Participants(
        Dealer dealer,
        Players players
) {

    public List<Participant> all() {
        final List<Participant> participants = new ArrayList<>();
        participants.add(dealer);
        participants.addAll(players.all());

        return participants;
    }
}
