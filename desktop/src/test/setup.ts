import "@testing-library/jest-dom/vitest";
import { Window } from "happy-dom";

const window = new Window();
// @ts-ignore
global.window = window;
// @ts-ignore
global.document = window.document;
// @ts-ignore
global.Node = window.Node;
// @ts-ignore
global.Element = window.Element;
// @ts-ignore
global.HTMLElement = window.HTMLElement;
// @ts-ignore
global.KeyboardEvent = window.KeyboardEvent;
// @ts-ignore
global.localStorage = window.localStorage;
// @ts-ignore
global.sessionStorage = window.sessionStorage;
// @ts-ignore
global.navigator = window.navigator;

