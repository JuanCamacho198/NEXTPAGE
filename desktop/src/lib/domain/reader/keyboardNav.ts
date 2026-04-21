const INPUT_LIKE_TAGS = new Set(["INPUT", "TEXTAREA", "SELECT", "BUTTON"]);

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
