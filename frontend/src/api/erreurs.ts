/** Erreur renvoyée par l'API au format ProblemDetail (RFC 9457), message destiné à l'écran. */
export class ErreurApi extends Error {
  readonly statut: number;

  constructor(message: string, statut: number) {
    super(message);
    this.name = "ErreurApi";
    this.statut = statut;
  }
}

interface Reponse<T> {
  data?: T;
  error?: unknown;
  response: Response;
}

function detail(erreur: unknown): string | null {
  if (erreur && typeof erreur === "object" && "detail" in erreur && typeof erreur.detail === "string") {
    return erreur.detail;
  }
  return null;
}

function messageParDefaut(statut: number): string {
  if (statut === 401) return "Session expirée, reconnectez-vous.";
  if (statut === 403) return "Action non autorisée.";
  if (statut >= 500) return "Le serveur a rencontré une erreur. Réessayez.";
  return "La demande a été refusée.";
}

/** Résultat d'un appel du client généré, ou une {@link ErreurApi}. */
export async function appel<T>(promesse: Promise<Reponse<T>>): Promise<T> {
  let resultat: Reponse<T>;
  try {
    resultat = await promesse;
  } catch {
    throw new ErreurApi("Connexion au serveur impossible. Vérifiez le réseau.", 0);
  }
  const { data, error, response } = resultat;
  if (!response.ok) {
    throw new ErreurApi(detail(error) ?? messageParDefaut(response.status), response.status);
  }
  return data as T;
}

export function messageErreur(erreur: unknown): string {
  if (erreur instanceof ErreurApi) return erreur.message;
  return "Une erreur inattendue est survenue.";
}
