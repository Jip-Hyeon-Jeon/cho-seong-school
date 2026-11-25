package com.jiphyeonjeon.choseongschool.client.view;

import java.awt.Dimension;
import java.awt.GridBagLayout;
import javax.swing.JPanel;

/** 팀 공통 1200x740 화면 안에서 대기실 중앙 정렬 */
public final class LobbyContainer extends JPanel {
    public LobbyContainer(LobbyView view) {
        super(new GridBagLayout());
        setBackground(Theme.BACKGROUND);
        setPreferredSize(new Dimension(Theme.SCREEN_WIDTH, Theme.SCREEN_HEIGHT));
        add(view);
    }
}
