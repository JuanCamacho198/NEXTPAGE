const INPUT_LIKE_TAGS = new Set(["INPUT", "TEXTAREA", "SELECT", "BUTTON"]);

const ARROW_INTENT_BY_KEY = {
  ArrowLeft: "prevPage",
  ArrowRight: "nextPage",
  ArrowUp: "scrollUp",
  ArrowDown: "scrollDown",
} as const;

export type ReaderArrowIntent = (typeof ARROW_INTENT_BY_KEY)[keyof typeof ARROW_INTENT_BY_KEY];

const hasEditableRole = (element: HTMLElement) => {
  const role = element.getAttribute("role");
  if (!role) {
    return false;
  }

  return role === "textbox" || role === "searchbox" || role === "combobox";
};

const isEditableElement = (element: HTMLElement) => {
  if (element.isContentEditable) {
    return true;
  }

  const contentEditableAttr = element.getAttribute("contenteditable");
  if (contentEditableAttr === "" || contentEditableAttr === "true") {
    return true;
  }

  if (element.contentEditable === "true") {
    return true;
  }

  if (INPUT_LIKE_TAGS.has(element.tagName)) {
    return true;
  }

  return hasEditableRole(element);
};

const hasEditableContext = (target: HTMLElement) => {
  if (isEditableElement(target)) {
    return true;
  }

  let parent = target.parentElement;
  while (parent) {
    if (isEditableElement(parent)) {
      return true;
    }
    parent = parent.parentElement;
  }

  return false;
};

export const canHandleReaderArrowNav = (event: KeyboardEvent) => {
  if (event.defaultPrevented) {
    return false;
  }

  if (event.altKey || event.ctrlKey || event.metaKey) {
    return false;
  }

  const target = event.target;
  if (!(target instanceof HTMLElement)) {
    return true;
  }

  if (hasEditableContext(target)) {
    return false;
  }

  return true;
};

export const resolveReaderArrowIntent = (event: KeyboardEvent): ReaderArrowIntent | null => {
  if (!canHandleReaderArrowNav(event)) {
    return null;
  }

  return ARROW_INTENT_BY_KEY[event.key as keyof typeof ARROW_INTENT_BY_KEY] ?? null;
};
