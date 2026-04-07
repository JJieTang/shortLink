export async function requireResponseBody<T>(
  responsePromise: Promise<T | undefined>,
  message: string,
) {
  const response = await responsePromise;

  if (response === undefined) {
    throw new Error(message);
  }

  return response;
}
