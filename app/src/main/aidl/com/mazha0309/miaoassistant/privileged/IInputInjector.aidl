package com.mazha0309.miaoassistant.privileged;

interface IInputInjector {
    void destroy() = 16777114;
    boolean replaceText(String text, int moveCursorLeft) = 1;
}
