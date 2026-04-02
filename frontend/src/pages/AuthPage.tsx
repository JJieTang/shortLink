const authCards = [
  {
    title: "Register",
    body: "This route will host the email and password onboarding flow that talks to /api/v1/auth/register.",
  },
  {
    title: "Login",
    body: "This route will submit credentials to /api/v1/auth/login and persist the returned access token.",
  },
  {
    title: "Refresh",
    body: "Session rotation infrastructure is already wired through the shared client and storage helpers.",
  },
];

export function AuthPage() {
  return (
    <section className="space-y-5">
      <SectionHeader
        eyebrow="Authentication"
        title="Account access shell"
        description="The page scaffolds the auth area before the actual forms land in the next commit."
      />

      <div className="grid gap-4 xl:grid-cols-3">
        {authCards.map((card) => (
          <article
            key={card.title}
            className="rounded-[28px] border border-ink/10 bg-white px-5 py-5 shadow-sm"
          >
            <p className="text-xs uppercase tracking-[0.24em] text-ember">{card.title}</p>
            <p className="mt-4 text-sm leading-6 text-ink/72">{card.body}</p>
          </article>
        ))}
      </div>
    </section>
  );
}

function SectionHeader(props: { eyebrow: string; title: string; description: string }) {
  return (
    <header className="space-y-2">
      <p className="text-xs font-semibold uppercase tracking-[0.3em] text-ember">{props.eyebrow}</p>
      <h2 className="text-2xl font-semibold tracking-tight text-ink">{props.title}</h2>
      <p className="max-w-2xl text-sm text-ink/70">{props.description}</p>
    </header>
  );
}
