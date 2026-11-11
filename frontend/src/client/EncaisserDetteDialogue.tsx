import { useState } from "react";
import { messageErreur } from "../api/erreurs";
import { useUtilisateur } from "../auth/AuthContext";
import { Dialogue } from "../ui/Dialogue";
import { dh, lireMontant } from "../ui/format";
import { ChoixClient } from "./ChoixClient";
import { Releve } from "./Releve";
import { type Client, libelleSolde, useReleve, useRembourser } from "./requetes";

/**
 * Encaisser une dette (Mon compte, ou fiche client pour l'admin) : le client rembourse en espèces, par TPE
 * ou les deux. L'argent s'ajoute au compte de l'utilisateur (la caisse principale pour l'admin) et la
 * dette du client baisse d'autant ; au-delà de la dette, le surplus devient une avance.
 */
export function EncaisserDetteDialogue({
  onFermer,
  clientInitial = null,
}: {
  onFermer: () => void;
  /** Client déjà choisi (fiche client) : pas de recherche. */
  clientInitial?: Client | null;
}) {
  const admin = useUtilisateur().role === "ADMIN";
  const [client, setClient] = useState<Client | null>(clientInitial);
  const [changer, setChanger] = useState(clientInitial === null);
  const [especes, setEspeces] = useState("");
  const [tpe, setTpe] = useState("");
  // Une clé par encaissement : un renvoi après coupure ne rembourse pas deux fois.
  const [cle, setCle] = useState(() => crypto.randomUUID());
  const rembourser = useRembourser();
  const releve = useReleve(client?.id ?? null);

  const montantEspeces = especes === "" ? 0 : lireMontant(especes);
  const montantTpe = tpe === "" ? 0 : lireMontant(tpe);
  const saisieValide = montantEspeces !== null && montantTpe !== null;
  const total = (montantEspeces ?? 0) + (montantTpe ?? 0);
  const dette = client?.dette ?? 0;
  const apres = dette - total;
  const valide = client !== null && saisieValide && total > 0.005;

  function choisir(c: Client) {
    setClient(c);
    setChanger(false);
    setEspeces("");
    setTpe("");
    setCle(crypto.randomUUID());
  }

  return (
    <Dialogue titre="Encaisser une dette" onFermer={onFermer} largeur={600}>
      <div className="formulaire encaisser-dette">
        {changer || !client ? (
          <>
            <span className="libelle">Client</span>
            <ChoixClient choisi={client} onChoisir={choisir} autoFocus seulementDettes />
          </>
        ) : (
          <div className="dette-client-choisi">
            <div>
              <span className="libelle">Client</span>
              <strong>{client.nom}</strong>
              <button type="button" className="lien-action" onClick={() => setChanger(true)}>
                Changer de client
              </button>
            </div>
            <div className="a-droite">
              <span className="libelle">{libelleSolde(dette)}</span>
              <strong className={dette > 0 ? "texte-dette" : "texte-secondaire"}>
                {dh(Math.abs(dette))}
              </strong>
            </div>
          </div>
        )}
        {client && !changer && (
          <>
            <>
              <div className="dette-montants">
                <ChampMontant
                  id="dette-especes"
                  libelle="En espèces"
                  valeur={especes}
                  onChange={setEspeces}
                  autoFocus
                />
                <ChampMontant id="dette-tpe" libelle="Par TPE" valeur={tpe} onChange={setTpe} />
              </div>
              <dl className="dette-apres">
                <div>
                  <dt>Remboursé</dt>
                  <dd>{dh(total)}</dd>
                </div>
                <div>
                  <dt>{admin ? "Ajouté à la caisse principale" : "Ajouté à mon compte"}</dt>
                  <dd className="texte-succes">+ {dh(total)}</dd>
                </div>
                <div className="dette-apres-total">
                  <dt>{apres < -0.005 ? "Avance du client" : "Dette restante"}</dt>
                  <dd>{dh(Math.abs(apres))}</dd>
                </div>
              </dl>
              {!saisieValide && <p className="erreur-champ">Montant invalide.</p>}
              {saisieValide && apres < -0.005 && (
                <p className="alerte alerte-attention">
                  Le remboursement dépasse la dette : {dh(-apres)} resteront en avance au client, déduits de
                  ses prochains achats à crédit.
                </p>
              )}
            </>
            <div className="dette-derniers">
              <span className="libelle">Derniers mouvements</span>
              <Releve requete={releve} limite={3} />
            </div>
          </>
        )}
        {rembourser.isError && (
          <p className="alerte alerte-danger" role="alert">
            {messageErreur(rembourser.error)}
          </p>
        )}
        <div className="rangee-boutons">
          <button type="button" className="bouton bouton-contour" onClick={onFermer}>
            Annuler
          </button>
          <button
            type="button"
            className="bouton bouton-plein"
            disabled={!valide || rembourser.isPending}
            onClick={() =>
              client &&
              rembourser.mutate(
                { clientId: client.id, especes: montantEspeces ?? 0, tpe: montantTpe ?? 0, cle },
                { onSuccess: onFermer },
              )
            }
          >
            {valide ? `Encaisser ${dh(total)}` : "Encaisser"}
          </button>
        </div>
      </div>
    </Dialogue>
  );
}

function ChampMontant({
  id,
  libelle,
  valeur,
  onChange,
  autoFocus = false,
}: {
  id: string;
  libelle: string;
  valeur: string;
  onChange: (valeur: string) => void;
  autoFocus?: boolean;
}) {
  return (
    <div className="champ">
      <label className="libelle" htmlFor={id}>
        {libelle}
      </label>
      <div className="champ-montant">
        <input
          id={id}
          inputMode="decimal"
          autoComplete="off"
          placeholder="0"
          value={valeur}
          onChange={(e) => onChange(e.target.value)}
          autoFocus={autoFocus}
        />
        <span>DH</span>
      </div>
    </div>
  );
}
