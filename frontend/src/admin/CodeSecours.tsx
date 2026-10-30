import { useState } from "react";
import { messageErreur } from "../api/erreurs";
import { Dialogue } from "../ui/Dialogue";
import { IconeBouclier } from "../ui/icones";
import { useNouveauCodeSecours } from "./requetes";

/** Régénère le code de secours de l'admin (mot de passe oublié) ; l'ancien cesse de fonctionner. */
export function CodeSecours() {
  const generer = useNouveauCodeSecours();
  const [confirmation, setConfirmation] = useState(false);

  return (
    <section className="admin-section">
      <div className="admin-tete">
        <div>
          <h1 className="admin-titre">Code de secours</h1>
          <p className="texte-secondaire">
            Le code de secours permet de redéfinir le mot de passe admin oublié, depuis l'écran de connexion.
            Il ne sert qu'une fois et n'est affiché qu'au moment où il est généré.
          </p>
        </div>
      </div>
      <div className="carte admin-carte">
        <p className="texte-secondaire">
          Générez un nouveau code si l'ancien a été perdu, utilisé ou vu par quelqu'un d'autre. Notez-le et
          rangez-le hors de l'ordinateur de la caisse.
        </p>
        <div className="rangee-boutons">
          <button
            type="button"
            className="bouton bouton-plein"
            disabled={generer.isPending}
            onClick={() => setConfirmation(true)}
          >
            Générer un nouveau code
          </button>
        </div>
      </div>
      {confirmation && (
        <Dialogue
          titre={generer.data ? "Nouveau code de secours" : "Remplacer le code de secours ?"}
          icone={<IconeBouclier />}
          ton={generer.data ? "succes" : "info"}
          onFermer={() => {
            setConfirmation(false);
            generer.reset();
          }}
        >
          {generer.data ? (
            <>
              <div className="encadre">
                <span className="libelle">À noter maintenant</span>
                <code className="code-secours" data-testid="code-secours">
                  {generer.data}
                </code>
              </div>
              <p className="texte-secondaire">
                Ce code ne sera plus jamais affiché. L'ancien code ne fonctionne plus.
              </p>
              <div className="rangee-boutons">
                <button
                  type="button"
                  className="bouton bouton-plein"
                  autoFocus
                  onClick={() => {
                    setConfirmation(false);
                    generer.reset();
                  }}
                >
                  Je l'ai noté
                </button>
              </div>
            </>
          ) : (
            <>
              <p className="texte-secondaire">L'ancien code cessera immédiatement de fonctionner.</p>
              {generer.isError && (
                <p className="alerte alerte-danger" role="alert">
                  {messageErreur(generer.error)}
                </p>
              )}
              <div className="rangee-boutons">
                <button
                  type="button"
                  className="bouton bouton-contour"
                  onClick={() => setConfirmation(false)}
                >
                  Annuler
                </button>
                <button
                  type="button"
                  className="bouton bouton-plein"
                  disabled={generer.isPending}
                  onClick={() => generer.mutate()}
                >
                  Générer
                </button>
              </div>
            </>
          )}
        </Dialogue>
      )}
    </section>
  );
}
