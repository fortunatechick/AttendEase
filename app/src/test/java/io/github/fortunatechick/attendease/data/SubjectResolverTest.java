package io.github.fortunatechick.attendease.data;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SubjectResolverTest {

    @Test
    public void firstImportCreatesEverySubject() {
        SubjectResolver.Result r = SubjectResolver.resolve(Collections.<String>emptyList(),
                Arrays.asList("Operating Systems", "Financial Der"));
        assertEquals(2, r.toCreate.size());
        assertTrue(r.renames.isEmpty());
        assertTrue(r.folds.isEmpty());
    }

    @Test
    public void sameNamesAgainCreateNothing() {
        SubjectResolver.Result r = SubjectResolver.resolve(
                Arrays.asList("Operating Systems", "Financial Der"),
                Arrays.asList("Operating Systems", "Financial Der"));
        assertTrue(r.toCreate.isEmpty());
        assertTrue(r.renames.isEmpty());
        assertTrue(r.folds.isEmpty());
    }

    @Test
    public void laterFullNameRenamesTruncatedSubject() {
        SubjectResolver.Result r = SubjectResolver.resolve(
                Collections.singletonList("Financial Der"),
                Collections.singletonList("Financial Derivatives"));
        assertEquals("Financial Derivatives", r.renames.get("Financial Der"));
        assertTrue(r.toCreate.isEmpty());
    }

    @Test
    public void laterTruncatedNameMapsToStoredFullName() {
        SubjectResolver.Result r = SubjectResolver.resolve(
                Collections.singletonList("Mobile Application Development"),
                Collections.singletonList("Mobile Application Develop"));
        assertEquals("Mobile Application Development", r.target.get("Mobile Application Develop"));
        assertTrue(r.toCreate.isEmpty());
        assertTrue(r.renames.isEmpty());
    }

    @Test
    public void newElectiveSharingAWordPrefixIsANewSubject() {
        SubjectResolver.Result r = SubjectResolver.resolve(
                Collections.singletonList("Machine Learning"),
                Collections.singletonList("Machine Learning Applications"));
        assertEquals(Collections.singleton("Machine Learning Applications"), r.toCreate);
        assertTrue(r.renames.isEmpty());
        assertTrue(r.folds.isEmpty());
    }

    @Test
    public void differentCapitalisationIsTheSameSubject() {
        SubjectResolver.Result r = SubjectResolver.resolve(
                Collections.singletonList("operating systems"),
                Collections.singletonList("Operating Systems"));
        assertTrue(r.toCreate.isEmpty());
        assertEquals("Operating Systems", r.renames.get("operating systems"));
    }

    @Test
    public void twoStoredSubjectsThatTurnOutToBeOneAreFolded() {
        SubjectResolver.Result r = SubjectResolver.resolve(
                Arrays.asList("Mobile Application Develop", "Mobile Application Development"),
                Collections.<String>emptyList());
        assertEquals("Mobile Application Development", r.folds.get("Mobile Application Develop"));
    }
}
