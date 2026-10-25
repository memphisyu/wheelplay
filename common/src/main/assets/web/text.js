/** Messages are supplied by Android resources before the application module loads. */
export function t(key, ...args) {
  const message = globalThis.wheelplayMessages?.[key] ?? key;
  return message.replace(/%(\d+)\$s/g, (_, index) => String(args[Number(index) - 1] ?? ''));
}
