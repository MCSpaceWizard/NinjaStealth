"""Tiny X11 input helper for driving the Minecraft client on virtual display :99 (XTEST).

Usage: xin.py ACTION [ACTION ...]
  key <keysym>        press a key (e.g. Return, Escape, F1, t)
  type <text>         type text
  cmd <command>       open chat, type the command, press Enter
  sleep <seconds>
  look <dx> <dy>      move the mouse relatively (turn the camera)
"""
import sys
import time

from Xlib import X, XK, display
from Xlib.ext import xtest

d = display.Display(':99')
root = d.screen().root


def find_window(w):
    try:
        name = w.get_wm_name()
    except Exception:
        name = None
    if name and 'Minecraft' in str(name):
        return w
    for child in w.query_tree().children:
        r = find_window(child)
        if r:
            return r
    return None


win = find_window(root)
if win:
    win.set_input_focus(X.RevertToParent, X.CurrentTime)
    d.sync()

NAMES = {' ': 'space', '/': 'slash', ':': 'colon', '_': 'underscore', '.': 'period', '-': 'minus', '~': 'asciitilde',
         '=': 'equal', ',': 'comma', '"': 'quotedbl', '?': 'question', '!': 'exclam', '[': 'bracketleft',
         ']': 'bracketright', '{': 'braceleft', '}': 'braceright', '@': 'at', '^': 'asciicircum'}
NEEDS_SHIFT = set(':_?!"~{}@^')


def press(keysym_name, shift=False):
    code = d.keysym_to_keycode(XK.string_to_keysym(keysym_name))
    shift_code = d.keysym_to_keycode(XK.string_to_keysym('Shift_L'))
    if shift:
        xtest.fake_input(d, X.KeyPress, shift_code)
    xtest.fake_input(d, X.KeyPress, code)
    time.sleep(0.02)
    xtest.fake_input(d, X.KeyRelease, code)
    if shift:
        xtest.fake_input(d, X.KeyRelease, shift_code)
    d.sync()
    time.sleep(0.03)


def type_text(text):
    for ch in text:
        press(NAMES.get(ch, ch), ch.isupper() or ch in NEEDS_SHIFT)


args = sys.argv[1:]
i = 0
while i < len(args):
    a = args[i]
    if a == 'key':
        press(args[i + 1]); i += 2
    elif a == 'type':
        type_text(args[i + 1]); i += 2
    elif a == 'cmd':
        press('t'); time.sleep(0.5); type_text(args[i + 1]); time.sleep(0.2); press('Return'); time.sleep(0.8); i += 2
    elif a == 'sleep':
        time.sleep(float(args[i + 1])); i += 2
    elif a == 'click':
        xtest.fake_input(d, X.MotionNotify, False, x=int(args[i + 1]), y=int(args[i + 2])); d.sync(); time.sleep(0.2)
        xtest.fake_input(d, X.ButtonPress, 1); d.sync(); time.sleep(0.05)
        xtest.fake_input(d, X.ButtonRelease, 1); d.sync(); time.sleep(0.3); i += 3
    elif a == 'rclick':
        xtest.fake_input(d, X.ButtonPress, 3); d.sync(); time.sleep(0.05)
        xtest.fake_input(d, X.ButtonRelease, 3); d.sync(); time.sleep(0.3); i += 1
    elif a == 'look':
        xtest.fake_input(d, X.MotionNotify, True, x=int(args[i + 1]), y=int(args[i + 2])); d.sync(); time.sleep(0.2); i += 3
    else:
        raise SystemExit('unknown action ' + a)
