package no.uio.ifi.localega.doa.repositories;

import no.uio.ifi.localega.doa.model.LEGADataset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigInteger;
import java.util.Collection;

@Repository
public interface DatasetRepository extends JpaRepository<LEGADataset, BigInteger> {

    Collection<LEGADataset> findByDatasetId(String datasetId);

}
