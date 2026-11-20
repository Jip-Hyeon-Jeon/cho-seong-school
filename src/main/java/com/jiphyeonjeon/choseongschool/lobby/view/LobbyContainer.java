package com.jiphyeonjeon.choseongschool.lobby.view;

import java.awt.Dimension;
import java.awt.GridBagLayout;
import javax.swing.JPanel;
import com.jiphyeonjeon.choseongschool.common.GameConstants;

/** 팀 공통 1600x900 화면 안에서 대기실 중앙 정렬 */
public final class LobbyContainer extends JPanel {
    public LobbyContainer(LobbyView view) {
        super(new GridBagLayout());
        setBackground(LobbyView.PAPER);
        setPreferredSize(new Dimension(GameConstants.Screen.WIDTH, GameConstants.Screen.HEIGHT));
        add(view);
    }
}
